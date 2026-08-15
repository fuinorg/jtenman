package org.fuin.jtenman.e2e;

import org.fuin.dsl.cqrs.common.basics.EmailAddress;
import org.fuin.jtenman.command.core.domain.tenants.ApplicationCatalogue;
import org.fuin.jtenman.command.core.domain.tenants.KeycloakTenantAdapter;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.SubjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Drives {@link KeycloakTenantAdapter} against a real Keycloak.
 * <p>
 * Everything this class asserts is a claim about Keycloak's behaviour rather than about jtenman's own
 * logic, which is why it cannot be a unit test: whether creating a role that exists is an error, whether a
 * group keeps a role mapping when the role is deleted, and whether a role mapped onto a group after a user
 * joined it still reaches that user - those are answers only Keycloak can give, and mocking them would be
 * writing down a guess and then verifying the guess.
 * <p>
 * It talks to the adapter directly rather than through jtenman's HTTP API. The command side, the event
 * store and the dispatcher are covered elsewhere; what is untested is the conversation with the identity
 * provider, so that is what this isolates.
 * <p>
 * The admin client is built once as {@code master}'s administrator, which stands in for the signed-in
 * administrator whose token the production provider uses. The production seam is the same
 * {@link KeycloakTenantAdapter.KeycloakProvider} either way.
 */
@Testcontainers
class KeycloakTenantAdapterIT {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.0.7";

    private static final String APP = "melkheftken";

    private static final String USER_ROLE = "melkheftken-user";

    private static final String ADMIN_ROLE = "melkheftken-admin";

    private static final String ADMIN_GROUP = "tenant-administrators";

    private static final String REALM_MANAGEMENT = "realm-management";

    private static final AtomicInteger REALM_COUNTER = new AtomicInteger();

    @Container
    @SuppressWarnings("resource") // Testcontainers closes it
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCommand("start-dev")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/master").forPort(8080).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static Keycloak admin;

    private static KeycloakTenantAdapter testee;

    @BeforeAll
    static void setUp() {
        admin = KeycloakBuilder.builder()
                .serverUrl(keycloakUrl())
                .realm("master")
                .clientId("admin-cli")
                .username("admin")
                .password("admin")
                .build();

        final ApplicationCatalogue catalogue = new ApplicationCatalogue(List.of(
                new ApplicationCatalogue.Entry(APP, "Melkheftken", "melkheftken-api", "melkheftken-api",
                        List.of(USER_ROLE, ADMIN_ROLE), List.of("manage-users", "view-users")),
                // An application that provisions no roles at all, to prove the code path is skipped
                // rather than failing on an empty list.
                new ApplicationCatalogue.Entry("plain", "Plain", "plain-api", "plain-api")));

        testee = new KeycloakTenantAdapter(() -> admin, catalogue, keycloakUrl());
    }

    @Test
    void subscribingCreatesTheRolesAndTheGroupThatCarriesThem() {
        final RealmName realm = newRealm();

        testee.createClient(realm, new ApplicationId(APP));

        assertThat(realmRoleNames(realm)).contains(USER_ROLE, ADMIN_ROLE);
        assertThat(groupRealmRoleNames(realm)).contains(USER_ROLE, ADMIN_ROLE);
    }

    @Test
    void subscribingGrantsTheRealmManagementRolesAsClientRoles() {
        // Client roles, not realm roles - they belong to Keycloak's own admin API. Getting this wrong is
        // invisible until the application tries to create a login and gets a 403.
        final RealmName realm = newRealm();

        testee.createClient(realm, new ApplicationId(APP));

        assertThat(groupClientRoleNames(realm, REALM_MANAGEMENT))
                .contains("manage-users", "view-users");
    }

    @Test
    void theApplicationsRolesAreRealmRolesNotClientRoles() {
        // melkheftken's JWT converter reads realm_access.roles only. A client role would be invisible
        // there and the check would fail with nothing wrong in Keycloak's UI to point at.
        final RealmName realm = newRealm();

        testee.createClient(realm, new ApplicationId(APP));

        assertThat(groupClientRoleNames(realm, "melkheftken-api")).doesNotContain(USER_ROLE, ADMIN_ROLE);
        assertThat(groupRealmRoleNames(realm)).contains(USER_ROLE, ADMIN_ROLE);
    }

    @Test
    void subscribeThenInviteLeavesTheAdministratorHoldingTheRoles() {
        final RealmName realm = newRealm();

        testee.createClient(realm, new ApplicationId(APP));
        final SubjectId subject = testee.inviteRealmAdministrator(realm, email(),
                List.of(new ApplicationId(APP)));

        assertThat(subject).isNotNull();
        assertThat(groupsOf(realm, subject)).contains("/" + ADMIN_GROUP);
        assertThat(effectiveRealmRolesOf(realm, subject)).contains(USER_ROLE, ADMIN_ROLE);
    }

    @Test
    void inviteThenSubscribeAlsoLeavesTheAdministratorHoldingTheRoles() {
        // The order nobody plans for. Inviting first creates the group with nothing in it; the later
        // subscribe maps the roles onto that same group, and Keycloak resolves group role mappings when it
        // mints a token - so the administrator picks them up without being re-invited.
        final RealmName realm = newRealm();

        final SubjectId subject = testee.inviteRealmAdministrator(realm, email(), List.of());
        testee.createClient(realm, new ApplicationId(APP));

        assertThat(groupsOf(realm, subject)).contains("/" + ADMIN_GROUP);
        assertThat(effectiveRealmRolesOf(realm, subject)).contains(USER_ROLE, ADMIN_ROLE);
    }

    @Test
    void invitingASecondAdministratorReEnsuresWithoutFailing() {
        // Both paths call ensureGroupRoles, so it runs repeatedly in normal use. Creating a role that
        // exists and mapping one that is already mapped must both be no-ops.
        final RealmName realm = newRealm();

        testee.createClient(realm, new ApplicationId(APP));
        testee.inviteRealmAdministrator(realm, email(), List.of(new ApplicationId(APP)));
        final SubjectId second = testee.inviteRealmAdministrator(realm, email(),
                List.of(new ApplicationId(APP)));

        assertThat(effectiveRealmRolesOf(realm, second)).contains(USER_ROLE, ADMIN_ROLE);
        assertThat(realmRoleNames(realm).stream().filter(USER_ROLE::equals).count()).isEqualTo(1);
    }

    @Test
    void unsubscribingRemovesTheRolesAgain() {
        // An application's role left behind in a realm that no longer subscribes is authority nothing
        // revokes.
        final RealmName realm = newRealm();
        testee.createClient(realm, new ApplicationId(APP));

        testee.removeClient(realm, new ApplicationId(APP));

        assertThat(realmRoleNames(realm)).doesNotContain(USER_ROLE, ADMIN_ROLE);
        assertThat(groupRealmRoleNames(realm)).doesNotContain(USER_ROLE, ADMIN_ROLE);
    }

    @Test
    void unsubscribingTwiceIsNotAnError() {
        final RealmName realm = newRealm();
        testee.createClient(realm, new ApplicationId(APP));

        testee.removeClient(realm, new ApplicationId(APP));

        assertThatCode(() -> testee.removeClient(realm, new ApplicationId(APP)))
                .doesNotThrowAnyException();
    }

    @Test
    void anApplicationWithoutRolesProvisionsNone() {
        final RealmName realm = newRealm();

        testee.createClient(realm, new ApplicationId("plain"));

        // The group is still created - inviteAdministrator needs it - but carries nothing.
        assertThat(realmRoleNames(realm)).doesNotContain(USER_ROLE, ADMIN_ROLE);
    }

    // ---------------------------------------------------------------------------------------------

    /** A fresh realm per test, so one test's roles cannot satisfy another's assertion. */
    private static RealmName newRealm() {
        final RealmName realm = new RealmName("t" + REALM_COUNTER.incrementAndGet());
        testee.createRealm(realm);
        return realm;
    }

    private static EmailAddress email() {
        return new EmailAddress("admin" + REALM_COUNTER.incrementAndGet() + "@acme.com");
    }

    private static List<String> realmRoleNames(final RealmName realm) {
        return realm(realm).roles().list().stream().map(RoleRepresentation::getName).toList();
    }

    private static List<String> groupRealmRoleNames(final RealmName realm) {
        return realm(realm).groups().group(adminGroupId(realm)).roles().realmLevel().listAll().stream()
                .map(RoleRepresentation::getName).toList();
    }

    private static List<String> groupClientRoleNames(final RealmName realm, final String clientId) {
        final var found = realm(realm).clients().findByClientId(clientId);
        if (found.isEmpty()) {
            return List.of();
        }
        return realm(realm).groups().group(adminGroupId(realm)).roles()
                .clientLevel(found.get(0).getId()).listAll().stream()
                .map(RoleRepresentation::getName).toList();
    }

    private static List<String> groupsOf(final RealmName realm, final SubjectId subject) {
        return realm(realm).users().get(subject.asBaseType()).groups().stream()
                .map(GroupRepresentation::getPath).toList();
    }

    /**
     * What the user actually holds, group mappings included - which is the question that matters, because
     * roles are never assigned to a user directly.
     */
    private static List<String> effectiveRealmRolesOf(final RealmName realm, final SubjectId subject) {
        return realm(realm).users().get(subject.asBaseType()).roles().realmLevel().listEffective().stream()
                .map(RoleRepresentation::getName).toList();
    }

    private static String adminGroupId(final RealmName realm) {
        return realm(realm).groups().groups().stream()
                .filter(group -> ADMIN_GROUP.equals(group.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No '" + ADMIN_GROUP + "' group in realm "
                        + realm.asBaseType()))
                .getId();
    }

    private static RealmResource realm(final RealmName realm) {
        return admin.realm(realm.asBaseType());
    }

    private static String keycloakUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

}

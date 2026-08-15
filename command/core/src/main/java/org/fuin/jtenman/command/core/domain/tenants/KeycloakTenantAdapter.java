package org.fuin.jtenman.command.core.domain.tenants;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.fuin.dsl.cqrs.common.basics.EmailAddress;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.DeleteTenantService;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.InviteAdministratorService;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.RegisterTenantService;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.ResumeTenantService;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.SubscribeApplicationService;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.SuspendTenantService;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.UnsubscribeApplicationService;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.IssuerUri;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.SubjectId;
import org.fuin.objects4j.common.ThreadSafe;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Carries out in Keycloak everything the {@link Tenant} aggregate decides.
 * <p>
 * One class implements all seven of the aggregate's SPIs: they are separate interfaces because each
 * method declares only the collaborator it needs, but there is only one thing behind them - the Keycloak
 * admin API - and splitting the adapter would spread one conversation over seven classes.
 * <p>
 * <b>Everything here runs with the caller's own rights.</b> The {@link Keycloak} client is supplied per
 * request, built from the signed-in administrator's token; jtenman holds no credential able to administer
 * Keycloak. See {@code steering/security.md}.
 */
@ThreadSafe
public class KeycloakTenantAdapter implements RegisterTenantService, InviteAdministratorService,
        SubscribeApplicationService, UnsubscribeApplicationService, SuspendTenantService,
        ResumeTenantService, DeleteTenantService {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakTenantAdapter.class);

    /** Name of the group a tenant's administrators are put into. */
    static final String TENANT_ADMIN_GROUP = "tenant-administrators";

    /** Keycloak's own client holding the administration roles, present in every realm. */
    static final String REALM_MANAGEMENT = "realm-management";

    /**
     * The same group as a path, which is what {@code UserRepresentation.setGroups} expects. Without the
     * leading slash Keycloak answers 500 with "Unable to find group specified by path".
     */
    private static final String TENANT_ADMIN_GROUP_PATH = "/" + TENANT_ADMIN_GROUP;

    /** Actions the invited person must complete before the account is usable. */
    private static final List<String> INVITE_ACTIONS = List.of("UPDATE_PASSWORD", "VERIFY_EMAIL");

    private final KeycloakProvider keycloakProvider;

    private final ApplicationCatalogue catalogue;

    private final String baseUrl;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param keycloakProvider Supplies an admin client authenticated as the current caller.
     * @param catalogue Applications of this system and their Keycloak names.
     * @param baseUrl Base URL of the Keycloak instance, used to build issuer URIs.
     */
    public KeycloakTenantAdapter(final KeycloakProvider keycloakProvider, final ApplicationCatalogue catalogue,
                                 final String baseUrl) {
        this.keycloakProvider = Objects.requireNonNull(keycloakProvider, "keycloakProvider==null");
        this.catalogue = Objects.requireNonNull(catalogue, "catalogue==null");
        this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl==null"));
    }

    @Override
    public boolean realmExists(final RealmName realm) {
        try {
            keycloak().realm(realm.asBaseType()).toRepresentation();
            return true;
        } catch (final NotFoundException ex) {
            return false;
        }
    }

    @Override
    public IssuerUri createRealm(final RealmName realm) {
        final RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm(realm.asBaseType());
        rep.setEnabled(true);
        keycloak().realms().create(rep);

        // The administrator group is NOT created here. Keycloak grants the roles that administer a realm
        // per realm, so the caller's token - minted before this realm existed - cannot yet touch anything
        // inside it. Creating the group here answers 403, and swallowing that leaves a realm whose group
        // is missing and an invitation that fails much later with "Unable to find group specified by
        // path". inviteAdministrator creates it instead, with a token that covers the realm.
        LOG.info("Created realm '{}'", realm.asBaseType());
        return new IssuerUri(issuerUri(realm));
    }

    @Override
    public SubjectId inviteRealmAdministrator(final RealmName realm, final EmailAddress email,
            final List<ApplicationId> subscribedApplications) {
        final RealmResource realmResource = keycloak().realm(realm.asBaseType());
        ensureAdminGroup(realmResource, realm);
        // Subscribing and inviting are independent commands and may arrive in either order. If a
        // subscription came first its roles are already mapped and this is a no-op; if it comes later,
        // createClient maps them onto this same group. Either way the administrator holds them at the
        // next login, because Keycloak resolves a group's role mappings when it mints the token.
        for (final ApplicationId application : subscribedApplications) {
            ensureGroupRoles(realmResource, realm, catalogue.require(application));
        }

        final UserRepresentation user = new UserRepresentation();
        user.setUsername(email.asBaseType());
        user.setEmail(email.asBaseType());
        user.setEnabled(true);
        user.setEmailVerified(false);
        // No credential is set. The person completes these actions through a one-time link, so jtenman
        // never generates, transmits or stores a password for any tenant.
        user.setRequiredActions(INVITE_ACTIONS);
        user.setGroups(List.of(TENANT_ADMIN_GROUP_PATH));

        final String id;
        try (Response response = realmResource.users().create(user)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new IllegalStateException("Could not create the administrator in realm '"
                        + realm.asBaseType() + "': HTTP " + response.getStatus());
            }
            id = idFromLocation(response);
        }

        try {
            realmResource.users().get(id).executeActionsEmail(INVITE_ACTIONS);
        } catch (final RuntimeException ex) {
            // The account exists and is unusable until the actions are done, so this is recoverable by
            // repeating the invitation rather than a reason to fail the whole operation. Keycloak needs
            // SMTP configured for the realm, which a fresh realm has not got.
            LOG.warn("Created the administrator in realm '{}' but could not send the invitation mail - "
                    + "repeat inviteAdministrator once SMTP is configured", realm.asBaseType(), ex);
        }

        LOG.info("Invited an administrator for realm '{}'", realm.asBaseType());
        return new SubjectId(id);
    }

    @Override
    public boolean isKnownApplication(final ApplicationId application) {
        return catalogue.contains(application);
    }

    @Override
    public void createClient(final RealmName realm, final ApplicationId application) {
        final ApplicationCatalogue.Entry entry = catalogue.require(application);
        final RealmResource realmResource = keycloak().realm(realm.asBaseType());

        final ClientRepresentation client = new ClientRepresentation();
        client.setClientId(entry.clientId());
        client.setName(entry.displayName());
        client.setEnabled(true);
        client.setPublicClient(true);
        client.setStandardFlowEnabled(true);
        // Authorization Code + PKCE, and nothing else: the applications are public clients with no secret
        // to ship, and the password grant stays off - see steering/security.md.
        client.setDirectAccessGrantsEnabled(false);
        client.setAttributes(Map.of("pkce.code.challenge.method", "S256"));
        client.setRedirectUris(List.of("http://localhost/*"));
        // Without this mapper the tokens carry only Keycloak's default "account" audience and the
        // application rejects every one of them.
        client.setProtocolMappers(List.of(audienceMapper(entry.audience())));

        try (Response response = realmResource.clients().create(client)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new IllegalStateException("Could not create client '" + entry.clientId() + "' in realm '"
                        + realm.asBaseType() + "': HTTP " + response.getStatus());
            }
        }
        LOG.info("Created client '{}' with audience '{}' in realm '{}'", entry.clientId(), entry.audience(),
                realm.asBaseType());

        // The application's roles, and the group that carries them. Done here rather than at invite time
        // because this is when the application enters the realm; inviteAdministrator re-ensures them so
        // that the reverse order works too. Both halves are idempotent.
        ensureAdminGroup(realmResource, realm);
        ensureGroupRoles(realmResource, realm, entry);
    }

    @Override
    public void removeClient(final RealmName realm, final ApplicationId application) {
        final ApplicationCatalogue.Entry entry = catalogue.require(application);
        final RealmResource realmResource = keycloak().realm(realm.asBaseType());
        final List<ClientRepresentation> found = realmResource.clients().findByClientId(entry.clientId());
        if (found.isEmpty()) {
            LOG.info("Client '{}' is already gone from realm '{}'", entry.clientId(), realm.asBaseType());
            return;
        }
        for (final ClientRepresentation client : found) {
            final ClientResource resource = realmResource.clients().get(client.getId());
            resource.remove();
        }
        // The realm roles go with the client, for the same reason the client goes: an application's role
        // left behind in a realm that no longer subscribes is authority that nothing revokes. Removing the
        // role removes it from the group too - Keycloak drops the mapping with it.
        for (final String roleName : entry.realmRoles()) {
            try {
                realmResource.roles().deleteRole(roleName);
                LOG.info("Removed realm role '{}' from realm '{}'", roleName, realm.asBaseType());
            } catch (final NotFoundException ex) {
                LOG.info("Realm role '{}' was already gone from realm '{}'", roleName, realm.asBaseType());
            }
        }
        LOG.info("Removed client '{}' from realm '{}'", entry.clientId(), realm.asBaseType());
    }

    @Override
    public void disableRealm(final RealmName realm) {
        setRealmEnabled(realm, false);
        LOG.info("Disabled realm '{}'", realm.asBaseType());
    }

    @Override
    public void enableRealm(final RealmName realm) {
        setRealmEnabled(realm, true);
        LOG.info("Enabled realm '{}'", realm.asBaseType());
    }

    @Override
    public void removeRealm(final RealmName realm) {
        try {
            keycloak().realm(realm.asBaseType()).remove();
            LOG.info("Removed realm '{}' and everything in it", realm.asBaseType());
        } catch (final NotFoundException ex) {
            // The caller wants it gone and it is gone.
            LOG.info("Realm '{}' was already gone", realm.asBaseType());
        }
    }

    /**
     * Makes sure the group a tenant's administrators are placed into exists.
     * <p>
     * Roles are only ever assigned to groups, never to a user directly - see {@code steering/security.md} -
     * so an administrator cannot be invited before this group is there. Which roles it carries is
     * {@link #ensureGroupRoles}' job, and depends on what the tenant is subscribed to; this method only
     * creates the group.
     *
     * @param realmResource Realm to create the group in.
     * @param realm Name of that realm, for the message.
     */
    private void ensureAdminGroup(final RealmResource realmResource, final RealmName realm) {
        final boolean exists = realmResource.groups().groups().stream()
                .anyMatch(group -> TENANT_ADMIN_GROUP.equals(group.getName()));
        if (exists) {
            return;
        }
        final GroupRepresentation group = new GroupRepresentation();
        group.setName(TENANT_ADMIN_GROUP);
        try (Response response = realmResource.groups().add(group)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw new IllegalStateException("Could not create the group '" + TENANT_ADMIN_GROUP
                        + "' in realm '" + realm.asBaseType() + "': HTTP " + response.getStatus());
            }
        }
        LOG.info("Created group '{}' in realm '{}'", TENANT_ADMIN_GROUP, realm.asBaseType());
    }

    /**
     * Makes sure an application's roles exist in the tenant's realm and are carried by the administrators
     * group.
     * <p>
     * Two kinds, and the difference matters. The application's own roles are <b>realm</b> roles, because an
     * application's JWT converter reads {@code realm_access.roles} - a client role is invisible there and
     * the check fails with nothing wrong in Keycloak's UI to point at. The {@code realm-management} roles
     * are <b>client</b> roles, because they belong to Keycloak's own admin API; they are what lets the
     * application create and remove logins under the caller's own token instead of holding a credential.
     * <p>
     * Both halves are idempotent: creating a role that exists and mapping one that is already mapped are
     * no-ops, because this runs on both the subscribe and the invite path and the two may happen in either
     * order.
     * <p>
     * <b>Known gap:</b> plain {@code manage-users} is realm-wide, which is more than the design wants. The
     * target is Keycloak's fine-grained admin permissions scoped to a group administrator's own subtree,
     * with {@code map-roles} denied - it is what would otherwise let a tenant administrator grant
     * themselves an application's most privileged role - and impersonation denied. Never grant
     * {@code manage-realm} or {@code realm-admin} here.
     *
     * @param realmResource Realm to work in.
     * @param realm Name of that realm, for the messages.
     * @param entry Application whose roles are ensured.
     */
    private void ensureGroupRoles(final RealmResource realmResource, final RealmName realm,
            final ApplicationCatalogue.Entry entry) {

        if (entry.realmRoles().isEmpty() && entry.realmManagementRoles().isEmpty()) {
            return;
        }

        final GroupRepresentation group = findAdminGroup(realmResource, realm);

        if (!entry.realmRoles().isEmpty()) {
            final List<RoleRepresentation> toMap = new ArrayList<>();
            for (final String roleName : entry.realmRoles()) {
                toMap.add(ensureRealmRole(realmResource, realm, roleName));
            }
            realmResource.groups().group(group.getId()).roles().realmLevel().add(toMap);
            LOG.info("Group '{}' in realm '{}' now carries realm role(s) {}", TENANT_ADMIN_GROUP,
                    realm.asBaseType(), entry.realmRoles());
        }

        if (!entry.realmManagementRoles().isEmpty()) {
            final List<ClientRepresentation> found = realmResource.clients().findByClientId(REALM_MANAGEMENT);
            if (found.isEmpty()) {
                throw new IllegalStateException("Realm '" + realm.asBaseType() + "' has no '"
                        + REALM_MANAGEMENT + "' client, so '" + entry.realmManagementRoles()
                        + "' cannot be granted");
            }
            final String clientUuid = found.get(0).getId();
            final ClientResource client = realmResource.clients().get(clientUuid);
            final List<RoleRepresentation> toMap = new ArrayList<>();
            for (final String roleName : entry.realmManagementRoles()) {
                toMap.add(client.roles().get(roleName).toRepresentation());
            }
            realmResource.groups().group(group.getId()).roles().clientLevel(clientUuid).add(toMap);
            LOG.info("Group '{}' in realm '{}' now carries {} client role(s) {}", TENANT_ADMIN_GROUP,
                    realm.asBaseType(), REALM_MANAGEMENT, entry.realmManagementRoles());
        }
    }

    /**
     * Returns the realm role, creating it if it is not there yet.
     *
     * @param realmResource Realm to work in.
     * @param realm Name of that realm, for the message.
     * @param roleName Role to ensure.
     * @return The role.
     */
    private RoleRepresentation ensureRealmRole(final RealmResource realmResource, final RealmName realm,
            final String roleName) {
        try {
            return realmResource.roles().get(roleName).toRepresentation();
        } catch (final NotFoundException ex) {
            final RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            realmResource.roles().create(role);
            LOG.info("Created realm role '{}' in realm '{}'", roleName, realm.asBaseType());
            return realmResource.roles().get(roleName).toRepresentation();
        }
    }

    /**
     * Returns the administrators group, which {@link #ensureAdminGroup} has created by now.
     *
     * @param realmResource Realm to look in.
     * @param realm Name of that realm, for the message.
     * @return The group.
     */
    private GroupRepresentation findAdminGroup(final RealmResource realmResource, final RealmName realm) {
        return realmResource.groups().groups().stream()
                .filter(group -> TENANT_ADMIN_GROUP.equals(group.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Group '" + TENANT_ADMIN_GROUP
                        + "' is missing from realm '" + realm.asBaseType() + "' right after it was ensured"));
    }

    private void setRealmEnabled(final RealmName realm, final boolean enabled) {
        final RealmResource realmResource = keycloak().realm(realm.asBaseType());
        final RealmRepresentation rep = realmResource.toRepresentation();
        rep.setEnabled(enabled);
        realmResource.update(rep);
    }

    private static ProtocolMapperRepresentation audienceMapper(final String audience) {
        final ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
        mapper.setName("audience");
        mapper.setProtocol("openid-connect");
        mapper.setProtocolMapper("oidc-audience-mapper");
        mapper.setConfig(Map.of(
                "included.custom.audience", audience,
                "access.token.claim", "true",
                "id.token.claim", "false"));
        return mapper;
    }

    private String issuerUri(final RealmName realm) {
        return baseUrl + "/realms/" + realm.asBaseType();
    }

    private Keycloak keycloak() {
        return keycloakProvider.get();
    }

    private static String idFromLocation(final Response response) {
        final String path = response.getLocation() == null ? null : response.getLocation().getPath();
        if (path == null || !path.contains("/")) {
            throw new IllegalStateException("Keycloak did not return the location of the created user");
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String stripTrailingSlash(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Supplies an admin client for the current caller.
     * <p>
     * A separate interface rather than an injected {@link Keycloak}, because the client is built from the
     * <em>caller's</em> token and therefore differs per request. It is also the seam the tests use to
     * point the adapter at a container.
     */
    @FunctionalInterface
    public interface KeycloakProvider {

        /**
         * Returns an admin client authenticated as the current caller.
         *
         * @return Keycloak admin client.
         */
        Keycloak get();

    }

}

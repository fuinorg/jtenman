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
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public SubjectId inviteRealmAdministrator(final RealmName realm, final EmailAddress email) {
        final RealmResource realmResource = keycloak().realm(realm.asBaseType());
        ensureAdminGroup(realmResource, realm);

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
     * Makes sure the group carrying the tenant-administrator role exists.
     * <p>
     * Roles are only ever assigned to groups, never to a user directly - see {@code steering/security.md} -
     * so an administrator cannot be invited before this group is there.
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

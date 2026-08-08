package org.fuin.jtenman.command.starter;

import org.fuin.jtenman.command.core.domain.tenants.ApplicationCatalogue;
import org.fuin.jtenman.command.core.domain.tenants.KeycloakTenantAdapter;
import org.fuin.jtenman.command.core.domain.tenants.TenantRepository;
import org.fuin.jtenman.command.core.handler.tenants.DeleteTenantCommandHandler;
import org.fuin.jtenman.command.core.handler.tenants.InviteAdministratorCommandHandler;
import org.fuin.jtenman.command.core.handler.tenants.RegisterTenantCommandHandler;
import org.fuin.jtenman.command.core.handler.tenants.ResumeTenantCommandHandler;
import org.fuin.jtenman.command.core.handler.tenants.SubscribeApplicationCommandHandler;
import org.fuin.jtenman.command.core.handler.tenants.SuspendTenantCommandHandler;
import org.fuin.jtenman.command.core.handler.tenants.UnsubscribeApplicationCommandHandler;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Registers the hand-written write-side beans of the tenants context: the command handlers and the
 * Keycloak adapter behind the aggregate's SPIs.
 * <p>
 * The generated {@code CommandBeansConfiguration} covers only what the generator knows about (the
 * aggregate repository); a command handler is hand-written, and nothing is component-scanned, so each one
 * has to be declared here. {@code CommandDispatcher} finds a handler through the Jandex index and then
 * takes it from the application context by class, which is why each one needs to be a bean.
 */
@Configuration
@EnableConfigurationProperties(JtenmanProperties.class)
public class TenantCommandConfiguration {

    /**
     * Creates the catalogue of applications from the configuration.
     *
     * @param properties Configured applications.
     *
     * @return New catalogue instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public ApplicationCatalogue applicationCatalogue(final JtenmanProperties properties) {
        return new ApplicationCatalogue(properties.getApplications().stream()
                .map(app -> new ApplicationCatalogue.Entry(app.getId(), app.getDisplayName(), app.getClientId(),
                        app.getAudience()))
                .toList());
    }

    /**
     * Creates the adapter that carries out in Keycloak what the aggregate decides.
     * <p>
     * The admin client is built per call from the <b>caller's own bearer token</b>, so every realm and
     * every user jtenman creates is created under the rights of the signed-in administrator. jtenman
     * holds no credential able to administer Keycloak - see {@code steering/security.md}.
     *
     * @param properties Where Keycloak lives.
     * @param catalogue Applications of this system.
     *
     * @return New adapter instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public KeycloakTenantAdapter keycloakTenantAdapter(final JtenmanProperties properties,
                                                       final ApplicationCatalogue catalogue) {
        return new KeycloakTenantAdapter(
                () -> KeycloakBuilder.builder()
                        .serverUrl(properties.getKeycloakUrl())
                        .realm("master")
                        .authorization("Bearer " + currentToken())
                        .build(),
                catalogue,
                properties.getKeycloakUrl());
    }

    /**
     * Creates the handler for registering a tenant.
     *
     * @param repository Repository the new tenant is stored in.
     * @param adapter Creates the realm.
     *
     * @return New handler instance.
     */
    @Bean
    public RegisterTenantCommandHandler registerTenantCommandHandler(final TenantRepository repository,
                                                                     final KeycloakTenantAdapter adapter) {
        return new RegisterTenantCommandHandler(repository, adapter);
    }

    /**
     * Creates the handler for inviting an administrator.
     *
     * @param repository Repository the tenant is loaded from.
     * @param adapter Creates the person and sends the invitation.
     *
     * @return New handler instance.
     */
    @Bean
    public InviteAdministratorCommandHandler inviteAdministratorCommandHandler(final TenantRepository repository,
                                                                               final KeycloakTenantAdapter adapter) {
        return new InviteAdministratorCommandHandler(repository, adapter);
    }

    /**
     * Creates the handler for subscribing a tenant to an application.
     *
     * @param repository Repository the tenant is loaded from.
     * @param adapter Resolves the catalogue and creates the client.
     *
     * @return New handler instance.
     */
    @Bean
    public SubscribeApplicationCommandHandler subscribeApplicationCommandHandler(final TenantRepository repository,
                                                                                 final KeycloakTenantAdapter adapter) {
        return new SubscribeApplicationCommandHandler(repository, adapter);
    }

    /**
     * Creates the handler for unsubscribing a tenant from an application.
     *
     * @param repository Repository the tenant is loaded from.
     * @param adapter Removes the client.
     *
     * @return New handler instance.
     */
    @Bean
    public UnsubscribeApplicationCommandHandler unsubscribeApplicationCommandHandler(
            final TenantRepository repository, final KeycloakTenantAdapter adapter) {
        return new UnsubscribeApplicationCommandHandler(repository, adapter);
    }

    /**
     * Creates the handler for suspending a tenant.
     *
     * @param repository Repository the tenant is loaded from.
     * @param adapter Disables the realm.
     *
     * @return New handler instance.
     */
    @Bean
    public SuspendTenantCommandHandler suspendTenantCommandHandler(final TenantRepository repository,
                                                                   final KeycloakTenantAdapter adapter) {
        return new SuspendTenantCommandHandler(repository, adapter);
    }

    /**
     * Creates the handler for resuming a tenant.
     *
     * @param repository Repository the tenant is loaded from.
     * @param adapter Enables the realm.
     *
     * @return New handler instance.
     */
    @Bean
    public ResumeTenantCommandHandler resumeTenantCommandHandler(final TenantRepository repository,
                                                                 final KeycloakTenantAdapter adapter) {
        return new ResumeTenantCommandHandler(repository, adapter);
    }

    /**
     * Creates the handler for deleting a tenant.
     *
     * @param repository Repository the tenant is loaded from.
     * @param adapter Removes the realm.
     *
     * @return New handler instance.
     */
    @Bean
    public DeleteTenantCommandHandler deleteTenantCommandHandler(final TenantRepository repository,
                                                                 final KeycloakTenantAdapter adapter) {
        return new DeleteTenantCommandHandler(repository, adapter);
    }

    /**
     * Returns the bearer token of the current caller.
     *
     * @return Raw token value.
     *
     * @throws IllegalStateException There is no authenticated caller, so nothing may be provisioned.
     */
    private static String currentToken() {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException(
                "No JWT for the current caller - jtenman provisions Keycloak only under the rights of a "
                        + "signed-in administrator and holds no credential of its own");
    }

}

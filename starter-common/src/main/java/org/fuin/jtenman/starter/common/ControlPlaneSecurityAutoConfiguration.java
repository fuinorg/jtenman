package org.fuin.jtenman.starter.common;

import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakJwtAuthenticationConverter;
import org.fuin.jtenman.shared.JtenmanRoles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Restricts jtenman to callers holding the realm role the operation needs.
 * <p>
 * Without this, every deployable falls back to Spring Boot's default resource-server chain, which asks
 * only {@code anyRequest().authenticated()}. In the control plane that is close to no authorization at
 * all: any person with an account in the administration realm and a token carrying the
 * {@code jtenman-api} audience could create realms, invite administrators and delete tenants. The
 * trust boundary pinned by {@link SingleRealmTenantAutoConfiguration} answers "which realm may speak to
 * us"; this answers "and who inside it may do what".
 *
 * <table>
 * <caption>What the chain enforces</caption>
 * <tr><th>Path</th><th>Requires</th></tr>
 * <tr><td>{@code /cmd/**}</td><td>{@link JtenmanRoles#TENANT_ADMIN}</td></tr>
 * <tr><td>{@code /view/**}</td><td>{@link JtenmanRoles#TENANT_ADMIN} or
 *     {@link JtenmanRoles#SVC_TENANT_READ}</td></tr>
 * <tr><td>everything else</td><td>an authenticated caller</td></tr>
 * </table>
 * <p>
 * The command paths are matched for <b>every</b> HTTP method rather than for {@code POST} only. A
 * method-scoped matcher would let any other verb on the same path fall through to the merely
 * authenticated rule below it, which is the classic way a rule like this is bypassed.
 * <p>
 * {@link JtenmanRoles#SVC_TENANT_READ} is admitted to the read side although the service account
 * carrying it does not exist yet - the list is what an administered application polls, and
 * {@code steering/security.md} settles that this pull is a machine identity rather than a person. Until
 * it is provisioned the read side is reachable with a {@link JtenmanRoles#TENANT_ADMIN} token, which is
 * what {@code doc/example/run-example.sh} uses.
 * <p>
 * <b>Only realm roles work here.</b> {@code KeycloakJwtAuthenticationConverter} maps
 * {@code realm_access.roles} and ignores client roles, so granting {@code tenant-admin} as a client role
 * of {@code jtenman-cli} produces a 403 with a Keycloak setup that looks correct.
 * <p>
 * Like {@link SingleRealmTenantAutoConfiguration} this is an auto-configuration rather than something
 * each deployable imports: all four reach it through their jtenman starter, and a fifth added later gets
 * it without anyone remembering to. It has to be ordered <b>before</b> {@link SecurityAutoConfiguration}
 * and {@link OAuth2ResourceServerAutoConfiguration}, whose own chains are
 * {@code @ConditionalOnDefaultWebSecurity} - that is {@code @ConditionalOnMissingBean(SecurityFilterChain)}
 * - and therefore have to see this one already registered to back off.
 * <p>
 * The bean itself is {@link ConditionalOnMissingBean}, so an application-supplied chain replaces it
 * whole. That is what the {@code *ApplicationIT} classes use to boot without a Keycloak, and it is the
 * one way this rule can be lost: a permit-all chain left outside test scope would silently take over.
 */
@AutoConfiguration(before = { SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ SecurityFilterChain.class, HttpSecurity.class })
public class ControlPlaneSecurityAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ControlPlaneSecurityAutoConfiguration.class);

    /**
     * Creates the one filter chain of a jtenman deployable.
     * <p>
     * CSRF is disabled and no session is created: every caller authenticates with a bearer token on each
     * request, so there is no cookie for a foreign site to ride on and nothing to keep between two
     * requests. Leaving the defaults on would reject every {@code POST /cmd/{type}} for a missing CSRF
     * token, which is not a security property here but an outage.
     *
     * @param http Chain being built.
     * @param jwtDecoder Tenant-aware decoder, normally the one of the Keycloak starter. Passed
     *                   explicitly rather than looked up by the configurer, so a missing or ambiguous
     *                   decoder fails the context with a readable message.
     * @param authenticationConverter Maps the token's realm roles to {@code ROLE_}-prefixed authorities.
     *                                Without it Spring Security's default converter runs, which knows
     *                                nothing about {@code realm_access} and grants no role at all.
     *
     * @return The chain.
     *
     * @throws Exception Building the chain failed.
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain controlPlaneFilterChain(
            final HttpSecurity http,
            final JwtDecoder jwtDecoder,
            final KeycloakJwtAuthenticationConverter authenticationConverter) throws Exception {

        // Logged at INFO for the same reason as the trust boundary: it is the whole authorization model
        // of a running instance in one line.
        LOG.info("Control plane secured - '/cmd/**' requires realm role '{}', '/view/**' requires '{}' or "
                        + "'{}', every other path an authenticated caller",
                JtenmanRoles.TENANT_ADMIN, JtenmanRoles.TENANT_ADMIN, JtenmanRoles.SVC_TENANT_READ);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/cmd/**").hasRole(JtenmanRoles.TENANT_ADMIN)
                        .requestMatchers("/view/**").hasAnyRole(JtenmanRoles.TENANT_ADMIN, JtenmanRoles.SVC_TENANT_READ)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                    jwt.decoder(jwtDecoder);
                    jwt.jwtAuthenticationConverter(authenticationConverter);
                }))
                .build();
    }

}

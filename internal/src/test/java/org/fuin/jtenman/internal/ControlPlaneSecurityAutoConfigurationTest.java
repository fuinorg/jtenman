package org.fuin.jtenman.internal;

import jakarta.servlet.Filter;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakJwtAuthenticationConverter;
import org.fuin.jtenman.shared.JtenmanRoles;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.ConfigurableWebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Test for {@link ControlPlaneSecurityAutoConfiguration}.
 * <p>
 * It drives real requests through the real filter chain rather than inspecting the configuration: a
 * chain nobody has watched reject anything is not evidence that it rejects anything. The three outcomes
 * that matter are all here - no token is a 401, a token without the role is a 403, and a token with it
 * gets through.
 * <p>
 * Nothing talks to Keycloak. {@link StubSecurityInfrastructure} supplies a decoder that reads the realm
 * roles straight out of the bearer value ({@code Bearer tenant-admin}), so the token still passes through
 * the production {@link KeycloakJwtAuthenticationConverter} and the {@code realm_access} mapping is part
 * of what is under test.
 */
class ControlPlaneSecurityAutoConfigurationTest {

    private static final String CMD_PATH = "/cmd/RegisterTenantCommand";

    private static final String VIEW_PATH = "/view/tenant/list-by-application?application=anyapp";

    private static final String HEALTH_PATH = "/actuator/health";

    /** A token whose caller is authenticated but holds no realm role at all. */
    private static final String NO_ROLE = "none";

    /** Marks a bearer value whose role is granted on the client rather than on the realm. */
    private static final String CLIENT_ROLE = "client.";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ControlPlaneSecurityAutoConfiguration.class,
                    SecurityAutoConfiguration.class,
                    OAuth2ResourceServerAutoConfiguration.class,
                    WebMvcAutoConfiguration.class))
            .withUserConfiguration(StubSecurityInfrastructure.class, StubEndpoints.class);

    /**
     * The reason this auto-configuration exists. Loaded beside Spring Boot's own chains - which ask no
     * more than {@code anyRequest().authenticated()} - exactly one chain must remain and it has to be
     * this one. Both of Boot's are {@code @ConditionalOnDefaultWebSecurity} and only back off if this
     * configuration was applied first, which is what the {@code before} attribute buys.
     */
    @Test
    void testItIsTheOnlyChainBesideSpringBootsDefaults() {

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            // Boot's default chain would answer 200 here, this one answers 403.
            assertThat(status(context, post(CMD_PATH), NO_ROLE)).isEqualTo(403);
        });

    }

    @Test
    void testCommandsRequireTheTenantAdminRole() {

        runner.run(context -> {
            assertThat(statusWithoutToken(context, post(CMD_PATH))).isEqualTo(401);
            assertThat(status(context, post(CMD_PATH), NO_ROLE)).isEqualTo(403);
            assertThat(status(context, post(CMD_PATH), JtenmanRoles.SVC_TENANT_READ)).isEqualTo(403);
            assertThat(status(context, post(CMD_PATH), JtenmanRoles.TENANT_ADMIN)).isEqualTo(200);
        });

    }

    /**
     * The read side takes either role: a person administering the control plane, or the machine identity
     * an administered application polls the list with.
     */
    @Test
    void testTheTenantListRequiresOneOfTheTwoRoles() {

        runner.run(context -> {
            assertThat(statusWithoutToken(context, get(VIEW_PATH))).isEqualTo(401);
            assertThat(status(context, get(VIEW_PATH), NO_ROLE)).isEqualTo(403);
            assertThat(status(context, get(VIEW_PATH), JtenmanRoles.TENANT_ADMIN)).isEqualTo(200);
            assertThat(status(context, get(VIEW_PATH), JtenmanRoles.SVC_TENANT_READ)).isEqualTo(200);
        });

    }

    /**
     * Everything the two rules above do not name still needs a token - the actuator included, which is
     * why {@code run-example.sh} sends one to reach the health endpoint. A role is not required there: an
     * authenticated caller of the administration realm may see whether the instance is up.
     */
    @Test
    void testEverythingElseNeedsAnAuthenticatedCallerAndNoRole() {

        runner.run(context -> {
            assertThat(statusWithoutToken(context, get(HEALTH_PATH))).isEqualTo(401);
            assertThat(status(context, get(HEALTH_PATH), NO_ROLE)).isEqualTo(200);
        });

    }

    /**
     * A client role is invisible to {@link KeycloakJwtAuthenticationConverter}, which maps
     * {@code realm_access.roles} only. Worth a test of its own because the failure is a 403 against a
     * Keycloak setup in which the role is plainly there - just granted on the client instead of the
     * realm.
     */
    @Test
    void testAClientRoleDoesNotOpenTheCommandEndpoint() {

        runner.run(context -> assertThat(statusWithClientRole(context)).isEqualTo(403));

    }

    /**
     * The chain is {@code @ConditionalOnMissingBean}, so a deployable - in practice its
     * {@code *ApplicationIT} - can replace it whole. That is deliberate, and it is also the one way the
     * rule above can be lost, so it is pinned here rather than left to be discovered.
     */
    @Test
    void testAnApplicationSuppliedChainWins() {

        runner.withUserConfiguration(PermitAllConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(statusWithoutToken(context, post(CMD_PATH))).isEqualTo(200);
        });

    }

    private static int statusWithoutToken(final AssertableWebApplicationContext context,
                                          final MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc(context).perform(request).andReturn().getResponse().getStatus();
    }

    private static int status(final AssertableWebApplicationContext context,
                              final MockHttpServletRequestBuilder request,
                              final String realmRoles) throws Exception {
        return mockMvc(context).perform(request.header("Authorization", "Bearer " + realmRoles))
                .andReturn().getResponse().getStatus();
    }

    private static int statusWithClientRole(final AssertableWebApplicationContext context) throws Exception {
        return status(context, post(CMD_PATH), CLIENT_ROLE + JtenmanRoles.TENANT_ADMIN);
    }

    private static MockMvc mockMvc(final AssertableWebApplicationContext context) {
        return MockMvcBuilders
                .webAppContextSetup(context.getSourceApplicationContext(ConfigurableWebApplicationContext.class))
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    /**
     * Stands in for the Keycloak starter: the tenant-aware decoder and the realm-role converter the
     * chain needs. The decoder does no cryptography - the bearer value <i>is</i> the realm role, several
     * of them separated by {@code +}, {@value #NO_ROLE} for none, and a value prefixed with
     * {@value #CLIENT_ROLE} lands in {@code resource_access} instead, which is how the client-role case
     * is exercised.
     * <p>
     * The encoding is limited to what Spring Security's {@code DefaultBearerTokenResolver} accepts as a
     * token - {@code [a-zA-Z0-9-._~+/]+=*}. A separator outside that set (a comma, a colon, or the empty
     * string for "no roles") never reaches the decoder at all: the header counts as malformed and the
     * request is a 401, which would silently pass a test expecting a 403.
     */
    @Configuration(proxyBeanMethods = false)
    static class StubSecurityInfrastructure {

        @Bean
        JwtDecoder jwtDecoder() {
            return StubSecurityInfrastructure::decode;
        }

        @Bean
        KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
            return new KeycloakJwtAuthenticationConverter();
        }

        private static Jwt decode(final String token) {
            final Jwt.Builder builder = Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("00000000-0000-0000-0000-000000000001")
                    .issuer("http://localhost:8180/realms/master")
                    .audience(List.of("jtenman-api"))
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(600));
            if (token.startsWith(CLIENT_ROLE)) {
                return builder.claim("resource_access",
                        Map.of("jtenman-cli", Map.of("roles", List.of(token.substring(CLIENT_ROLE.length()))))).build();
            }
            return builder.claim("realm_access", Map.of("roles", roles(token))).build();
        }

        private static List<String> roles(final String token) {
            if (NO_ROLE.equals(token)) {
                return List.of();
            }
            return List.of(token.split("\\+"));
        }

    }

    @RestController
    static class StubEndpoints {

        @PostMapping("/cmd/{type}")
        String cmd(@PathVariable("type") final String type) {
            return type;
        }

        @GetMapping("/view/tenant/list-by-application")
        String listByApplication() {
            return "[]";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "{\"status\":\"UP\"}";
        }

    }

    /**
     * What the {@code *ApplicationIT} classes declare so they can boot without a Keycloak.
     */
    @Configuration(proxyBeanMethods = false)
    static class PermitAllConfiguration {

        @Bean
        SecurityFilterChain permitAll(final HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

    }

}

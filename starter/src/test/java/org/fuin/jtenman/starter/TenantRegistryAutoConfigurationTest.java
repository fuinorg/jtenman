package org.fuin.jtenman.starter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.starter.KeycloakSecurityAutoConfiguration;
import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link TenantRegistryAutoConfiguration}.
 * <p>
 * It drives the real pull over real HTTP against a small server that answers with the bytes jtenman
 * actually sends. Everything in between is production code - the generated {@code @HttpExchange} proxy,
 * the private object mapper, the value-object deserializers, the admission list.
 * <p>
 * <b>The response body is a capture, not an invention.</b> It was taken from a running
 * {@code jtenman-combined} with one registered and subscribed tenant:
 *
 * <pre>
 * curl -s -H "Authorization: Bearer $TOKEN" \
 *   'http://localhost:9090/view/tenant/list-by-application?application=melkheftken'
 * </pre>
 * <p>
 * Only the issuer URI was pointed at this test's server, so the OpenID Connect discovery that resolves a
 * tenant's keys has somewhere to go. A hand-written body would prove the starter can read what this test
 * writes, which is not the question - the question is whether it can read what jtenman writes.
 */
class TenantRegistryAutoConfigurationTest {

    private static final String APPLICATION = "melkheftken";

    private static final String REALM = "acme";

    private HttpServer server;

    private List<String> authorizationHeaders;

    private int port;

    @BeforeEach
    void startJtenmanStandIn() throws IOException {

        authorizationHeaders = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/view/tenant/list-by-application", exchange -> {
            authorizationHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            if (!("application=" + APPLICATION).equals(exchange.getRequestURI().getQuery())) {
                respond(exchange, 400, "unexpected query: " + exchange.getRequestURI().getQuery());
                return;
            }
            respond(exchange, 200, tenantList());
        });

        // Stands in for the tenant's Keycloak realm, so resolving its verification material succeeds.
        server.createContext("/realms/" + REALM + "/.well-known/openid-configuration",
                exchange -> respond(exchange, 200, openidConfiguration()));

        server.start();
    }

    @AfterEach
    void stopJtenmanStandIn() {
        server.stop(0);
    }

    /**
     * The reason this starter exists. Loaded beside the Keycloak starter, the repository that survives has
     * to be the one fed by jtenman - the starter's own {@link KeycloakTenantRepository} discovers and
     * accepts every realm of the instance, which is no admission control and no revocation.
     */
    @Test
    void testItReplacesTheDiscoveringRepositoryOfTheKeycloakStarter() {

        runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JwtTenantRepository.class);
            assertThat(context).doesNotHaveBean(KeycloakTenantRepository.class);
            assertThat(context.getBean(JwtTenantRepository.class)).isInstanceOf(JtenmanTenantRepository.class);
        });

    }

    /**
     * The whole chain in one assertion: the list was fetched while the context started, the response was
     * read with the value objects intact, and the realm it named is now the one realm this application
     * accepts.
     */
    @Test
    void testTheListIsFetchedWhileTheContextStartsAndDecidesWhoIsAccepted() {

        runner().run(context -> {
            final JtenmanTenantRepository repository = context.getBean(JtenmanTenantRepository.class);

            assertThat(repository.usable()).isTrue();
            assertThat(repository.lastSuccessfulRefresh()).isPresent();
            assertThat(repository.getTenantIds()).containsExactly(new TenantId(REALM));
            assertThat(repository.findByIssuer(issuer(REALM))).isPresent();
            assertThat(repository.findByIssuer(issuer("intruder"))).isEmpty();
        });

    }

    /**
     * jtenman requires a role on the tenant list, so the pull needs a token. Without a provider the
     * starter sends none and jtenman answers 401 - a loud, closed failure rather than a quiet one.
     */
    @Test
    void testTheTokenOfTheAuthProviderIsSent() {

        runner().run(context -> assertThat(authorizationHeaders).containsExactly("null"));

        authorizationHeaders.clear();

        runner().withUserConfiguration(FixedTokenConfiguration.class)
                .run(context -> assertThat(authorizationHeaders).containsExactly("Bearer svc-tenant-read-token"));

    }

    /**
     * Naming a client registration switches the pull from unauthenticated to the service account. The
     * no-op provider has to back off completely - two providers would leave which token is sent to bean
     * ordering.
     */
    @Test
    void testAClientRegistrationReplacesTheNoOpProvider() {

        runner().run(context ->
                assertThat(context).hasSingleBean(TenantListAuthProvider.class)
                        .getBean(TenantListAuthProvider.class)
                        .isInstanceOf(NoOpTenantListAuthProvider.class));

        runner().withUserConfiguration(ServiceAccountConfiguration.class)
                .withPropertyValues("jtenman.registry.client-registration-id=jtenman")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TenantListAuthProvider.class);
                    assertThat(context).doesNotHaveBean(NoOpTenantListAuthProvider.class);
                    assertThat(context.getBean(TenantListAuthProvider.class))
                            .isInstanceOf(ClientCredentialsTenantListAuthProvider.class);
                });

    }

    /**
     * An application that cannot reach jtenman while it starts still has to come up - refusing would tie
     * every rollout to the control plane being up at that moment - but it accepts nobody until a later
     * pull succeeds.
     */
    @Test
    void testAnUnreachableJtenmanDoesNotStopTheContextButAcceptsNobody() {

        stopJtenmanStandIn();

        runner().run(context -> {
            assertThat(context).hasNotFailed();
            final JtenmanTenantRepository repository = context.getBean(JtenmanTenantRepository.class);
            assertThat(repository.usable()).isFalse();
            assertThat(repository.getTenantIds()).isEmpty();
            assertThat(repository.findByIssuer(issuer(REALM))).isEmpty();
        });

    }

    @Test
    void nothingIsContributedWithoutAUrl() {

        // The starter has to be able to sit on an application's class path without taking it over. Taking
        // this repository moves an application's trust boundary out of its own configuration and into the
        // registry, and makes its authentication depend on the registry being reachable - a decision it
        // cannot take deliberately if adding a dependency is enough to make it.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TenantListClientCredentialsAutoConfiguration.class,
                        TenantRegistryAutoConfiguration.class))
                .withPropertyValues("jtenman.registry.application=" + APPLICATION)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JtenmanTenantRepository.class);
                    assertThat(context).doesNotHaveBean(TenantRegistryRefresher.class);
                });
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TenantListClientCredentialsAutoConfiguration.class,
                        TenantRegistryAutoConfiguration.class,
                        KeycloakSecurityAutoConfiguration.class))
                .withPropertyValues(
                        "jtenman.registry.url=http://localhost:" + port,
                        "jtenman.registry.application=" + APPLICATION,
                        // Long enough that only the pull performed while the context starts is observed.
                        "jtenman.registry.refresh-interval=1h",
                        // Mandatory for the keycloak starter, which refuses to start without an audience.
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer("master"),
                        "spring.security.oauth2.resourceserver.jwt.audiences=billing-api");
    }

    private String issuer(final String realm) {
        return "http://localhost:" + port + "/realms/" + realm;
    }

    /** The captured answer of jtenman - see the class comment. */
    private String tenantList() {
        return "[{\"source\":{\"entityIdPath\":\"TENANT " + REALM + "\",\"aggregateVersion\":0},"
                + "\"realm\":\"" + REALM + "\","
                + "\"issuerUri\":\"" + issuer(REALM) + "\","
                + "\"status\":\"ACTIVE\"}]";
    }

    private String openidConfiguration() {
        return "{\"issuer\":\"" + issuer(REALM) + "\","
                + "\"jwks_uri\":\"" + issuer(REALM) + "/protocol/openid-connect/certs\","
                + "\"authorization_endpoint\":\"" + issuer(REALM) + "/protocol/openid-connect/auth\","
                + "\"token_endpoint\":\"" + issuer(REALM) + "/protocol/openid-connect/token\","
                + "\"response_types_supported\":[\"code\"],"
                + "\"subject_types_supported\":[\"public\"],"
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"]}";
    }

    private static void respond(final HttpExchange exchange, final int status, final String body)
            throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** What a real deployment declares: the token of the {@code svc-tenant-read} service account. */
    @Configuration(proxyBeanMethods = false)
    static class FixedTokenConfiguration {

        @Bean
        TenantListAuthProvider tenantListAuthProvider() {
            return () -> Optional.of("svc-tenant-read-token");
        }

    }

    /**
     * Stands in for Spring Boot's OAuth2 client auto-configuration, which builds these two from
     * {@code spring.security.oauth2.client.registration}. Nothing here asks the token endpoint for
     * anything - what is under test is which provider the context ends up with.
     */
    @Configuration(proxyBeanMethods = false)
    static class ServiceAccountConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration
                    .withRegistrationId("jtenman")
                    .clientId("billing-svc")
                    .clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .tokenUri("http://localhost:8180/realms/master/protocol/openid-connect/token")
                    .build());
        }

        @Bean
        OAuth2AuthorizedClientService authorizedClientService(final ClientRegistrationRepository repository) {
            return new InMemoryOAuth2AuthorizedClientService(repository);
        }

    }

}

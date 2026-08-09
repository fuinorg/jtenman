package org.fuin.jtenman.starter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.fuin.cqrs4j.springboot.keycloak.starter.KeycloakSecurityAutoConfiguration;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.jtenman.test.helper.StubJtenman;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code jtenman-test-helper}'s stub against this starter, which is what its users will do.
 * <p>
 * {@link TenantRegistryAutoConfigurationTest} beside this one is the direct evidence: it answers with a
 * captured response from a running jtenman, so it shows the starter can read what <b>jtenman</b> writes.
 * That test stays exactly as it is and is not replaced by this one.
 * <p>
 * This one answers a different question. {@link StubJtenman} is shipped for other applications to build
 * their tests on, and a stub is only worth shipping if the production reading path actually accepts it.
 * Its own test pins its output against the same capture; this closes the loop by running the real
 * auto-configuration - the generated {@code @HttpExchange} proxy, the private object mapper, the
 * value-object deserializers - against it. If the two ever disagree, the failure lands here rather than
 * in somebody else's repository.
 */
class StubJtenmanUsableTest {

    private static final String APPLICATION = "billing";

    private static final String REALM = "acme";

    private StubJtenman jtenman;

    private HttpServer keycloak;

    @BeforeEach
    void setUp() throws IOException {
        jtenman = StubJtenman.start();
        // Stands in for the tenant's realm, so resolving its verification material succeeds.
        keycloak = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        keycloak.createContext("/realms/" + REALM + "/.well-known/openid-configuration",
                exchange -> respond(exchange, openidConfiguration()));
        keycloak.start();
    }

    @AfterEach
    void tearDown() {
        jtenman.close();
        keycloak.stop(0);
    }

    @Test
    void testTheStarterReadsWhatTheStubServes() {

        // GIVEN
        jtenman.subscribe(APPLICATION, REALM, URI.create(issuer(REALM)));

        // WHEN & THEN
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            final JtenmanTenantRepository repository = context.getBean(JtenmanTenantRepository.class);
            assertThat(repository.usable()).isTrue();
            assertThat(repository.getTenantIds()).containsExactly(new TenantId(REALM));
            assertThat(repository.findByIssuer(issuer(REALM))).isPresent();
        });

        assertThat(jtenman.requests()).singleElement()
                .satisfies(request -> assertThat(request.application()).contains(APPLICATION));

    }

    /**
     * Revocation, which is the reason a consuming application would reach for this stub at all: it is the
     * one behaviour of the starter that cannot be observed without something able to change its mind.
     */
    @Test
    void testATenantTheStubDropsStopsBeingAccepted() {

        // GIVEN
        jtenman.subscribe(APPLICATION, REALM, URI.create(issuer(REALM)));

        // WHEN & THEN
        runner().run(context -> {
            final JtenmanTenantRepository repository = context.getBean(JtenmanTenantRepository.class);
            assertThat(repository.getTenantIds()).containsExactly(new TenantId(REALM));

            jtenman.unsubscribe(APPLICATION, REALM);
            repository.refresh();

            assertThat(repository.getTenantIds()).isEmpty();
            assertThat(repository.findByIssuer(issuer(REALM))).isEmpty();
        });

    }

    /**
     * And the outage case: a control plane that stops answering leaves the previous list in place, which
     * is what the staleness bound then limits.
     */
    @Test
    void testAnOutageLeavesThePreviousListInPlace() {

        // GIVEN
        jtenman.subscribe(APPLICATION, REALM, URI.create(issuer(REALM)));

        // WHEN & THEN
        runner().run(context -> {
            final JtenmanTenantRepository repository = context.getBean(JtenmanTenantRepository.class);
            jtenman.answerWith(503);

            assertThat(org.assertj.core.api.Assertions.catchThrowable(repository::refresh))
                    .describedAs("a failed pull propagates").isNotNull();
            assertThat(repository.getTenantIds()).containsExactly(new TenantId(REALM));
        });

    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TenantListClientCredentialsAutoConfiguration.class,
                        TenantRegistryAutoConfiguration.class,
                        KeycloakSecurityAutoConfiguration.class))
                .withPropertyValues(
                        "jtenman.registry.url=" + jtenman.url(),
                        "jtenman.registry.application=" + APPLICATION,
                        // Long enough that only the pull performed while the context starts is observed.
                        "jtenman.registry.refresh-interval=1h",
                        // Mandatory for the keycloak starter, which refuses to start without an audience.
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer("master"),
                        "spring.security.oauth2.resourceserver.jwt.audiences=billing-api");
    }

    private String issuer(final String realm) {
        return "http://localhost:" + keycloak.getAddress().getPort() + "/realms/" + realm;
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

    private static void respond(final HttpExchange exchange, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

}

package org.fuin.jtenman.combined;

import org.fuin.cqrs4j.test.helper.TestHelper;
import org.fuin.jtenman.shared.JtenmanRoles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the deployable really does refuse a caller without the role, over HTTP, against the
 * controllers it actually ships.
 * <p>
 * This is the counterpart to {@link JtenmanApplicationIT}: that one replaces the security chain with
 * permit-all and therefore proves nothing about who may call what. Here the production chain from
 * {@code ControlPlaneSecurityAutoConfiguration} is the one under test, which also pins the two things
 * that would otherwise drift apart unnoticed - the paths the chain matches and the paths the generated
 * controllers are mapped to. {@code TenantController} is regenerated on every build, so its mapping is
 * not something a reviewer can rely on staying put.
 * <p>
 * No Keycloak is started. {@link StubJwtDecoder} replaces the tenant-aware decoder with one that reads
 * the realm roles out of the bearer value, so everything after decoding - the realm-role mapping in
 * {@code KeycloakJwtAuthenticationConverter}, the matchers, the role checks - is the production code.
 * The bearer value is limited to what Spring Security's {@code DefaultBearerTokenResolver} accepts as a
 * token: a value outside {@code [a-zA-Z0-9-._~+/]+=*} never reaches the decoder and answers 401, which
 * would quietly satisfy a test looking for a rejection.
 */
@Testcontainers
@SpringBootTest(classes = {JtenmanApplication.class, ControlPlaneAuthorizationIT.StubJwtDecoder.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ControlPlaneAuthorizationIT {

    /** Version of the KurrentDB image the whole project is tested against. */
    private static final String KURRENTDB_VERSION = "26.1";

    /** Bearer value of an authenticated caller holding no realm role at all. */
    private static final String NO_ROLE = "none";

    private static final String CMD_PATH = "/cmd/RegisterTenantCommand";

    /**
     * An application from the catalogue in {@code application.yml}. Nothing is subscribed to it here, so
     * a caller that gets through the chain reads an empty list.
     */
    private static final String VIEW_PATH = "/view/tenant/list-by-application?application=melkheftken";

    private static final String HEALTH_PATH = "/actuator/health";

    @Container
    @SuppressWarnings("resource") // Testcontainers closes it
    static final GenericContainer<?> EVENTSTORE = TestHelper.createEventstoreContainer(KURRENTDB_VERSION);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Points the application at the container's dynamically mapped port.
     *
     * @param registry Registry the properties are added to.
     */
    @DynamicPropertySource
    static void eventStoreProperties(final DynamicPropertyRegistry registry) {
        registry.add("jtenman.eventstore.host", EVENTSTORE::getHost);
        registry.add("jtenman.eventstore.port", () -> EVENTSTORE.getMappedPort(2113));
        registry.add("jtenman.eventstore.tls", () -> false);
    }

    /**
     * The command endpoint is the privileged one: it creates realms and invites administrators. What it
     * answers a {@code tenant-admin} is the dispatcher's business - the point here is only that the
     * chain lets that caller through and stops the other two.
     */
    @Test
    void commandsRequireTheTenantAdminRole() {

        assertThat(post(CMD_PATH, null).getStatusCode().value()).isEqualTo(401);
        assertThat(post(CMD_PATH, NO_ROLE).getStatusCode().value()).isEqualTo(403);
        assertThat(post(CMD_PATH, JtenmanRoles.SVC_TENANT_READ).getStatusCode().value()).isEqualTo(403);
        assertThat(post(CMD_PATH, JtenmanRoles.TENANT_ADMIN).getStatusCode().value()).isNotIn(401, 403);

    }

    /**
     * The tenant list is what an administered application polls, so it takes the machine role beside the
     * administrator's - and nothing else.
     */
    @Test
    void theTenantListRequiresOneOfTheTwoRoles() {

        assertThat(get(VIEW_PATH, null).getStatusCode().value()).isEqualTo(401);
        assertThat(get(VIEW_PATH, NO_ROLE).getStatusCode().value()).isEqualTo(403);

        final ResponseEntity<String> administrator = get(VIEW_PATH, JtenmanRoles.TENANT_ADMIN);
        assertThat(administrator.getStatusCode().value()).isEqualTo(200);
        assertThat(administrator.getBody()).isEqualTo("[]");

        assertThat(get(VIEW_PATH, JtenmanRoles.SVC_TENANT_READ).getStatusCode().value()).isEqualTo(200);

    }

    /**
     * Health answers without a token.
     * <p>
     * <b>This changed</b> when jtenman adopted the shared chain from
     * {@code cqrs-4-java-springboot-security}: it used to require one. The reason to change it is that a
     * container orchestrator's probe has no token, so an authenticated health endpoint makes the
     * liveness check either impossible or a place to put a credential. Opening it is a decision, made
     * here rather than inherited by accident - the shared chain's {@code permit-actuator} can close it
     * again in one line.
     * <p>
     * A token still works, which is what keeps {@code doc/example/run-example.sh} valid.
     */
    @Test
    void healthAnswersWithAndWithoutAToken() {

        final ResponseEntity<String> anonymous = get(HEALTH_PATH, null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(200);
        assertThat(anonymous.getBody()).contains("\"status\":\"UP\"");

        final ResponseEntity<String> response = get(HEALTH_PATH, NO_ROLE);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");

    }

    private ResponseEntity<String> get(final String path, final String realmRoles) {
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers(realmRoles)), String.class);
    }

    private ResponseEntity<String> post(final String path, final String realmRoles) {
        final HttpHeaders headers = headers(realmRoles);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
    }

    private String url(final String path) {
        return "http://localhost:" + port + path;
    }

    private static HttpHeaders headers(final String realmRoles) {
        final HttpHeaders headers = new HttpHeaders();
        if (realmRoles != null) {
            headers.setBearerAuth(realmRoles);
        }
        return headers;
    }

    /**
     * Replaces the tenant-aware decoder of the Keycloak starter, which would need a running identity
     * provider and a signed token. Its bean is {@code @ConditionalOnMissingBean}, so this one wins
     * without any exclusion. Test scope only.
     */
    @TestConfiguration
    static class StubJwtDecoder {

        @Bean
        JwtDecoder jwtDecoder() {
            return StubJwtDecoder::decode;
        }

        private static Jwt decode(final String token) {
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject("00000000-0000-0000-0000-000000000001")
                    .issuer("http://localhost:8180/realms/master")
                    .audience(List.of("jtenman-api"))
                    .issuedAt(Instant.now().minusSeconds(60))
                    .expiresAt(Instant.now().plusSeconds(600))
                    .claim("realm_access", Map.of("roles", roles(token)))
                    .build();
        }

        private static List<String> roles(final String token) {
            if (NO_ROLE.equals(token)) {
                return List.of();
            }
            return List.of(token.split("\\+"));
        }

    }

}

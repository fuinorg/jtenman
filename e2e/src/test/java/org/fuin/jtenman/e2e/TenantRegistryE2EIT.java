/**
 * Copyright (C) 2026 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.jtenman.e2e;

import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.ReadStreamOptions;
import io.kurrent.dbclient.ResolvedEvent;
import org.fuin.cqrs4j.test.helper.TestHelper;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.fuin.jtenman.combined.JtenmanApplication;
import org.fuin.jtenman.starter.ClientCredentialsTenantListAuthProvider;
import org.fuin.jtenman.starter.JtenmanTenantRepository;
import org.fuin.jtenman.starter.TenantListAuthProvider;
import org.fuin.jtenman.starter.TenantListClientCredentialsAutoConfiguration;
import org.fuin.jtenman.starter.TenantRegistryAutoConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole chain, with nothing stubbed: a real Keycloak, a real jtenman, and a real administered
 * application replicating its tenant list through {@code jtenman-starter}.
 * <p>
 * Everything the other tests fake is real here - the service account and its client secret, the audience
 * mapper, the realm role granted through a group, the OpenID Connect discovery of the tenant realm, the
 * JSON on the wire. What it proves that they cannot:
 *
 * <ol>
 * <li>An application configured the way the README says ends up trusting exactly the realms jtenman lists
 *     for it.</li>
 * <li><b>Revocation reaches it.</b> A tenant unsubscribed in the control plane stops being accepted
 *     within one refresh interval, and the removal is announced so the cached issuer validator and key
 *     selector drop it.</li>
 * <li>The service account may read the list and nothing else.</li>
 * <li>The provisioning audit trail names the administrator who actually signed in - a real OpenID
 *     Connect {@code sub}, not a placeholder.</li>
 * </ol>
 *
 * <h2>The consumer is a second, separate application context</h2>
 * <p>
 * jtenman boots as {@code @SpringBootTest}; the administered application is built with an
 * {@link ApplicationContextRunner} in the test method. They share a JVM and nothing else, which is what
 * lets one process play both sides.
 * <p>
 * The two starter auto-configurations are <b>excluded from jtenman's own context</b>. Both modules are on
 * this module's class path - the only place in the repository where that is true - and without the
 * exclusion jtenman would pick up the consumer's repository from the imports file and stop pinning its
 * trust boundary to the administration realm. That is exactly the accident the exclusion documents.
 */
@Testcontainers
@SpringBootTest(classes = JtenmanApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.autoconfigure.exclude="
                + "org.fuin.jtenman.starter.TenantRegistryAutoConfiguration,"
                + "org.fuin.jtenman.starter.TenantListClientCredentialsAutoConfiguration")
class TenantRegistryE2EIT {

    /** Version of the KurrentDB image the whole project is tested against. */
    private static final String KURRENTDB_VERSION = "26.1";

    /** Same image as docker-compose.yml, so a developer's machine already has it. */
    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.0.7";

    /** The application this test plays, as named in jtenman's catalogue in application.yml. */
    private static final String APPLICATION = "melkheftken";

    /** How often the administered application re-reads the list. Short, because the test waits for it. */
    private static final Duration REFRESH = Duration.ofSeconds(1);

    private static final AtomicInteger REALM_COUNTER = new AtomicInteger();

    @Container
    @SuppressWarnings("resource") // Testcontainers closes it
    static final GenericContainer<?> EVENTSTORE = TestHelper.createEventstoreContainer(KURRENTDB_VERSION);

    @Container
    @SuppressWarnings("resource") // Testcontainers closes it
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE))
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCommand("start-dev")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/master").forPort(8080).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static KeycloakFixture keycloak;

    @LocalServerPort
    private int port;

    @Autowired
    private KurrentDBClient kurrentDBClient;

    private final RestClient rest = RestClient.create();

    /**
     * Points jtenman at both containers.
     * <p>
     * The issuer is registered as a supplier rather than a value: this runs before Testcontainers has
     * necessarily started anything, and the mapped port only exists afterwards. Keycloak in
     * {@code start-dev} derives its issuer from the request, so the URL the test uses and the {@code iss}
     * claim it mints agree without any further configuration.
     *
     * @param registry Registry the properties are added to.
     */
    @DynamicPropertySource
    static void containerProperties(final DynamicPropertyRegistry registry) {
        registry.add("jtenman.eventstore.host", EVENTSTORE::getHost);
        registry.add("jtenman.eventstore.port", () -> EVENTSTORE.getMappedPort(2113));
        registry.add("jtenman.eventstore.tls", () -> false);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> keycloakUrl() + "/realms/master");
        // Two separate settings pointing at the same Keycloak, and both have to move: the issuer is the
        // realm jtenman accepts tokens from, this is the base URL it administers realms through.
        registry.add("jtenman.keycloak-url", TenantRegistryE2EIT::keycloakUrl);
    }

    @BeforeAll
    static void provisionKeycloak() {
        keycloak = new KeycloakFixture(keycloakUrl(), "admin", "admin");
        keycloak.provision();
    }

    /**
     * The scenario the control plane exists for: an application is told which realms are its tenants, and
     * is told again when one stops being.
     */
    @Test
    void theApplicationReplicatesItsTenantsAndStopsAcceptingARevokedOne() {

        final String realm = registerAndSubscribeTenant();
        final TenantId tenantId = new TenantId(realm);

        consumer().run(context -> {
            assertThat(context).hasNotFailed();

            // The service account is really being used - not the no-op fallback that sends no token.
            assertThat(context.getBean(TenantListAuthProvider.class))
                    .isInstanceOf(ClientCredentialsTenantListAuthProvider.class);

            final JtenmanTenantRepository tenants = context.getBean(JtenmanTenantRepository.class);
            final RemovalListener removals = context.getBean(RemovalListener.class);

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(tenants.getTenantIds()).contains(tenantId);
                // Resolved against the tenant realm Keycloak really created - this is the material the
                // application would verify that tenant's tokens with.
                assertThat(tenants.findByIssuer(issuerOf(realm))).isPresent();
            });

            unsubscribeTenant(realm);

            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(tenants.findByIssuer(issuerOf(realm))).isEmpty();
                assertThat(tenants.getTenantIds()).doesNotContain(tenantId);
            });

            // The announcement is the whole of the second revocation layer: it is what evicts the cached
            // issuer validator and key selector of the tenant that went away.
            assertThat(removals.removed).contains(tenantId);
        });

    }

    /**
     * jtenman's event stream is the provisioning audit trail, so every event has to name who caused it.
     * <p>
     * Worth an end-to-end assertion rather than a unit test of {@code AuditedRepository}: the subject id
     * only becomes real once a token from a real identity provider has been decoded, and the value here
     * is the administrator's actual OpenID Connect {@code sub} - the same one Keycloak would delete a
     * user by.
     */
    @Test
    void theStoredEventsNameTheAdministratorWhoCausedThem() throws Exception {

        final String realm = registerAndSubscribeTenant();

        final String metadata = readFirstEventMetadata("TENANT-" + realm);

        // The meta type names the payload below it and is what the read side resolves in the registry.
        assertThat(metadata).contains("\"meta-type\":\"CommandMeta\"");
        assertThat(metadata.replaceAll("\\s", "")).containsOnlyOnce("\"CommandMeta\":{");

        // Not "anonymousUser", and not the client id either - the subject of the human who signed in.
        assertThat(metadata).contains(administratorSubjectId());

    }

    /**
     * A machine role is transport authority, not domain authority. The account that reads the list must
     * not be able to change anything.
     */
    @Test
    void theServiceAccountMayReadTheListAndNothingElse() {

        final String token = keycloak.serviceAccountToken();

        assertThat(get("/view/tenant/list-by-application?application=" + APPLICATION, token)
                .getStatusCode().value()).isEqualTo(200);

        assertThat(post("/cmd/RegisterTenantCommand", token, "{}").getStatusCode().value()).isEqualTo(403);

    }

    /**
     * Builds the administered application: the two starter auto-configurations plus Spring Boot's OAuth2
     * client, configured exactly as {@code starter/README.md} tells a consumer to configure them.
     *
     * @return Runner for the consumer's context.
     */
    private ApplicationContextRunner consumer() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        OAuth2ClientAutoConfiguration.class,
                        TenantListClientCredentialsAutoConfiguration.class,
                        TenantRegistryAutoConfiguration.class))
                .withUserConfiguration(RemovalListener.class)
                .withPropertyValues(
                        "jtenman.registry.url=http://localhost:" + port,
                        "jtenman.registry.application=" + APPLICATION,
                        "jtenman.registry.refresh-interval=" + REFRESH.toMillis() + "ms",
                        "jtenman.registry.client-registration-id=jtenman",
                        "spring.security.oauth2.client.registration.jtenman.client-id="
                                + KeycloakFixture.SVC_CLIENT,
                        "spring.security.oauth2.client.registration.jtenman.client-secret="
                                + keycloak.serviceAccountSecret(),
                        "spring.security.oauth2.client.registration.jtenman.authorization-grant-type="
                                + "client_credentials",
                        "spring.security.oauth2.client.provider.jtenman.token-uri=" + keycloak.tokenUri());
    }

    /**
     * Registers a tenant and subscribes it to this application, as an administrator would.
     *
     * @return Realm name of the new tenant.
     */
    private String registerAndSubscribeTenant() {
        final String realm = "e2e" + REALM_COUNTER.incrementAndGet();
        command("RegisterTenantCommand", realm, "\"realm\": \"" + realm + "\",");
        command("InviteAdministratorCommand", realm, "\"email\": \"admin@" + realm + ".example.com\",");
        command("SubscribeApplicationCommand", realm, "\"application\": \"" + APPLICATION + "\",");
        return realm;
    }

    private void unsubscribeTenant(final String realm) {
        command("UnsubscribeApplicationCommand", realm, "\"application\": \"" + APPLICATION + "\",");
    }

    /**
     * Posts one command. A fresh administrator token per call, because Keycloak grants the rights over a
     * realm per realm - one minted before {@code registerTenant} created it carries none over it.
     *
     * @param type Command type, which is both the path segment and the {@code eventType} in the body.
     * @param realm Realm the command addresses.
     * @param payload Type specific fields, already serialized and comma terminated.
     */
    private void command(final String type, final String realm, final String payload) {
        final String body = "{"
                + payload
                + "\"eventType\": \"" + type + "\","
                + "\"event-id\": \"" + UUID.randomUUID() + "\","
                + "\"event-timestamp\": \"2026-08-09T10:00:00+02:00\","
                + "\"correlation-id\": null,"
                + "\"causation-id\": null,"
                + "\"entity-id-path\": \"TENANT " + realm + "\","
                + "\"aggregate-version\": null}";
        final ResponseEntity<String> response = post("/cmd/" + type, keycloak.administratorToken(), body);
        assertThat(response.getStatusCode().value())
                .describedAs("%s for realm %s: %s", type, realm, response.getBody())
                .isEqualTo(200);
    }

    /**
     * Reads the metadata of the first event of a stream, straight from the event store.
     *
     * @param streamName Name of the aggregate's stream.
     *
     * @return Metadata as it was stored.
     *
     * @throws Exception Reading the stream failed.
     */
    private String readFirstEventMetadata(final String streamName) throws Exception {
        final List<ResolvedEvent> events = kurrentDBClient
                .readStream(streamName, ReadStreamOptions.get().forwards().fromStart().maxCount(1))
                .get().getEvents();
        assertThat(events).describedAs("events in stream " + streamName).isNotEmpty();
        return new String(events.getFirst().getOriginalEvent().getUserMetadata(), StandardCharsets.UTF_8);
    }

    /**
     * Returns the {@code sub} of the administrator, read out of their own token - so the expected value
     * comes from Keycloak rather than from this test.
     *
     * @return Subject id.
     */
    private String administratorSubjectId() {
        final String payload = keycloak.administratorToken().split("\\.")[1];
        final String json = new String(java.util.Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        final java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).describedAs("sub claim in %s", json).isTrue();
        return matcher.group(1);
    }

    private ResponseEntity<String> get(final String path, final String token) {
        return rest.get().uri("http://localhost:" + port + path)
                .header("Authorization", "Bearer " + token)
                .retrieve().onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);
    }

    private ResponseEntity<String> post(final String path, final String token, final String body) {
        return rest.post().uri("http://localhost:" + port + path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);
    }

    private static String issuerOf(final String realm) {
        return keycloakUrl() + "/realms/" + realm;
    }

    private static String keycloakUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    /** Records the tenants the replica announced as gone. */
    @Configuration(proxyBeanMethods = false)
    static class RemovalListener {

        private final List<TenantId> removed = new CopyOnWriteArrayList<>();

        @EventListener
        public void onTenantRemoved(final TenantRemovedEvent event) {
            removed.add(event.tenant().getTenantId());
        }

    }

}

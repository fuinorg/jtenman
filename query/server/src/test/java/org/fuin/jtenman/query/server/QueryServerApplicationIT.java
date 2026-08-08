package org.fuin.jtenman.query.server;

import org.fuin.cqrs4j.test.helper.TestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the query side boots and reports itself as healthy. It needs KurrentDB to subscribe its projections to.
 * <p>
 * Only KurrentDB is started as a container: jtenman's read model lives in an in-memory H2 database, and
 * no Keycloak is needed because the security chain is replaced below - what is under test here is that
 * the application context is complete and the deployable comes up, not who may call it.
 */
@Testcontainers
@SpringBootTest(classes = {QueryServerApplication.class, QueryServerApplicationIT.PermitAllSecurity.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueryServerApplicationIT {

    /** Version of the KurrentDB image the whole project is tested against. */
    private static final String KURRENTDB_VERSION = "26.1";

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

    @Test
    void applicationStartsAndIsHealthy() {

        final ResponseEntity<String> response =
                restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    /**
     * Replaces the Keycloak-backed chain, which would otherwise need a running identity provider and a
     * token just to reach the health endpoint. Test scope only - the production chain is the one
     * described in {@code steering/security.md}.
     */
    @TestConfiguration
    static class PermitAllSecurity {

        @Bean
        SecurityFilterChain permitAll(final HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

    }

}

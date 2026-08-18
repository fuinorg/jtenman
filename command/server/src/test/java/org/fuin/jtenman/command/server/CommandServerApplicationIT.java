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
package org.fuin.jtenman.command.server;

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
 * Verifies that the command side boots against a real KurrentDB instance and reports itself as healthy. The event store is the only external system the write side needs - there is no relational database on it.
 * <p>
 * Only KurrentDB is started as a container: jtenman's read model lives in an in-memory H2 database, and
 * no Keycloak is needed because the security chain is replaced below - what is under test here is that
 * the application context is complete and the deployable comes up, not who may call it.
 */
@Testcontainers
@SpringBootTest(classes = {CommandServerApplication.class, CommandServerApplicationIT.PermitAllSecurity.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommandServerApplicationIT {

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

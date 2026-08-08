package org.fuin.jtenman.query.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the query side (the CQRS read responsibility) as a standalone Spring Boot application. This is the side consuming applications poll for the tenants of their application.
 * <p>
 * jtenman is the tenant control plane and has no tenants of its own: it runs with
 * {@code org.fuin.cqrs4j.multitenancy=false} and pins its trust boundary to one realm (see
 * {@code steering/security.md}).
 */
@SpringBootApplication
public class QueryServerApplication {

    private QueryServerApplication() {
    }

    /**
     * Application entry point.
     *
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        SpringApplication.run(QueryServerApplication.class, args);
    }

}

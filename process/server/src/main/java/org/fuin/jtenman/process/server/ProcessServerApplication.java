package org.fuin.jtenman.process.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the process managers as a standalone Spring Boot application.
 * <p>
 * jtenman is the tenant control plane and has no tenants of its own: it runs with
 * {@code org.fuin.cqrs4j.multitenancy=false} and pins its trust boundary to one realm (see
 * {@code steering/security.md}).
 */
@SpringBootApplication
public class ProcessServerApplication {

    private ProcessServerApplication() {
    }

    /**
     * Application entry point.
     *
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        SpringApplication.run(ProcessServerApplication.class, args);
    }

}

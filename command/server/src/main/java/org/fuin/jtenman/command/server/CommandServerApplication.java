package org.fuin.jtenman.command.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the command side (the CQRS write responsibility) as a standalone Spring Boot application. Tenants are administered by sending commands to its generic command endpoint.
 * <p>
 * jtenman is the tenant control plane and has no tenants of its own: it runs with
 * {@code org.fuin.cqrs4j.multitenancy=false} and pins its trust boundary to one realm (see
 * {@code steering/security.md}).
 */
@SpringBootApplication
public class CommandServerApplication {

    private CommandServerApplication() {
    }

    /**
     * Application entry point.
     *
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        SpringApplication.run(CommandServerApplication.class, args);
    }

}

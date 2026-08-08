package org.fuin.jtenman.combined;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the command, query and process sides as a single deployable - the normal way to run jtenman,
 * which is an administrative application with no need to scale its sides apart.
 * <p>
 * jtenman is the tenant control plane and has no tenants of its own: it runs with
 * {@code org.fuin.cqrs4j.multitenancy=false} and pins its trust boundary to one realm (see
 * {@code steering/security.md}).
 */
@SpringBootApplication
public class JtenmanApplication {

    private JtenmanApplication() {
    }

    /**
     * Application entry point.
     *
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        SpringApplication.run(JtenmanApplication.class, args);
    }

}

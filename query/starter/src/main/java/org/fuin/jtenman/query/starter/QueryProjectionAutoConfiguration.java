package org.fuin.jtenman.query.starter;

import org.fuin.jtenman.starter.common.EventStoreProperties;
import io.kurrent.dbclient.KurrentDBClientSettings;
import io.kurrent.dbclient.KurrentDBProjectionManagementClient;
import org.fuin.esc.api.ProjectionAdminEventStore;
import org.fuin.esc.esgrpc.GrpcProjectionAdminEventStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the read side on top of the shared event store configuration: the projection admin
 * connection used to create and reset KurrentDB projections, plus the JPA backed bookkeeping
 * (projection positions and leases) that {@code SpringViewManager} needs.
 *
 * <p>The bookkeeping beans and their entities live in the {@code org.fuin.cqrs4j} packages, which are
 * outside the application's own component scan, so both are pulled in explicitly here. The
 * application's read-model entities are added to the same scan because declaring {@code @EntityScan}
 * replaces Spring Boot's default of scanning the main application package.
 *
 * <p>Scheduling is enabled here because the view manager drives its projections from a scheduled
 * task and therefore needs the {@code ScheduledAnnotationBeanPostProcessor}.
 */
@AutoConfiguration
@Import(QueryBeansConfiguration.class)
@EnableScheduling
@EntityScan("org.fuin.jtenman.query.core.view")
public class QueryProjectionAutoConfiguration {

    /**
     * Creates the client used to administer KurrentDB projections.
     *
     * @param config Connection settings.
     *
     * @return Projection management client.
     */
    @Bean
    @ConditionalOnMissingBean
    public KurrentDBProjectionManagementClient kurrentDBProjectionManagementClient(
            final EventStoreProperties config) {
        final KurrentDBClientSettings settings = KurrentDBClientSettings.builder()
                .addHost(config.getHost(), config.getPort())
                .defaultCredentials(config.getUser(), config.getPassword())
                .tls(config.isTls())
                .buildConnectionSettings();
        return KurrentDBProjectionManagementClient.create(settings);
    }

    /**
     * Creates the projection admin event store the view manager uses to set up its projections.
     *
     * @param client Projection management client to use.
     *
     * @return Opened projection admin event store.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ProjectionAdminEventStore projectionAdminEventStore(
            final KurrentDBProjectionManagementClient client) {
        return new GrpcProjectionAdminEventStore(client, null).open();
    }

}

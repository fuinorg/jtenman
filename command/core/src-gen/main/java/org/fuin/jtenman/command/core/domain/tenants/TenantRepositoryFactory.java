package org.fuin.jtenman.command.core.domain.tenants;

import org.fuin.esc.api.EventStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates a TenantRepository.
 */
@Configuration
public class TenantRepositoryFactory {

    /**
     * Produces a TenantRepository.
     *
     * @param eventStore The event store to use for construction.
     *
     * @return The new repository instance.
     */
    @Bean
    public TenantRepository tenantRepository(final EventStore eventStore) {
        return new TenantRepository(eventStore);
    }

}

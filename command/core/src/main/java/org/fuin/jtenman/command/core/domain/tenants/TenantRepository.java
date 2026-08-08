package org.fuin.jtenman.command.core.domain.tenants;

import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.esc.EventStoreRepository;
import org.fuin.esc.api.EventStore;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;

/**
 * Repository that is capable of storing a {@link Tenant}.
 */
public final class TenantRepository extends EventStoreRepository<TenantRealmId, Tenant> {

    /**
     * Constructor with all mandatory data.
     * 
     * @param eventStore Event store.
     */
    public TenantRepository(final EventStore eventStore) {
        super(eventStore);
    }

    @Override
    public Class<Tenant> getAggregateClass() {
        return Tenant.class;
    }

    @Override
    public final EntityType getAggregateType() {
        return TenantRealmId.TYPE;
    }

    @Override
    public final Tenant create() {
        return new Tenant();
    }

    @Override
    protected final String getIdParamName() {
        return "tenantRealmId";
    }

}

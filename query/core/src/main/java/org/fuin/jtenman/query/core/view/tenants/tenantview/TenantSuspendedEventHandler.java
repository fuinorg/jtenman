package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.EventHandler;
import org.fuin.ddd4j.core.EventType;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.fuin.jtenman.shared.domain.tenants.TenantSuspendedEvent;

/**
 * Marks the tenant suspended. The subscriptions are left untouched, so resuming restores exactly the
 * previous set - and the query filters on the status rather than on the subscriptions.
 */
public class TenantSuspendedEventHandler implements EventHandler<TenantSuspendedEvent> {

    @Override
    public EventType getEventType() {
        return TenantSuspendedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(final EntityManager em, final TenantSuspendedEvent event) {
        TenantReadModel.setStatus(em, TenantReadModel.realmOf(event.getEntityIdPath()), TenantStatus.SUSPENDED);
    }

}

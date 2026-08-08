package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.EventHandler;
import org.fuin.ddd4j.core.EventType;
import org.fuin.jtenman.shared.domain.tenants.TenantResumedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;

/**
 * Marks the tenant active again.
 */
public class TenantResumedEventHandler implements EventHandler<TenantResumedEvent> {

    @Override
    public EventType getEventType() {
        return TenantResumedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(final EntityManager em, final TenantResumedEvent event) {
        TenantReadModel.setStatus(em, TenantReadModel.realmOf(event.getEntityIdPath()), TenantStatus.ACTIVE);
    }

}

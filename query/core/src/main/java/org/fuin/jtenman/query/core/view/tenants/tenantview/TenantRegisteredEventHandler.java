package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.EventHandler;
import org.fuin.ddd4j.core.EventType;
import org.fuin.jtenman.shared.domain.tenants.TenantRegisteredEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;

import java.util.Objects;

/**
 * Creates the tenant row. No subscription exists yet, so the tenant appears in no application's list
 * until it is subscribed - which is exactly right: registered is not the same as usable.
 */
public class TenantRegisteredEventHandler implements EventHandler<TenantRegisteredEvent> {

    @Override
    public EventType getEventType() {
        return TenantRegisteredEvent.EVENT_TYPE;
    }

    @Override
    public void handle(final EntityManager em, final TenantRegisteredEvent event) {
        final String realm = TenantReadModel.realmOf(event.getEntityIdPath());
        final TenantEntity entity = new TenantEntity();
        entity.setRealm(realm);
        entity.setIssuerUri(event.getIssuerUri().asBaseType());
        entity.setStatus(TenantStatus.ACTIVE.name());
        entity.setEntityIdPath(event.getEntityIdPath().asString());
        // Every applied event carries the version the aggregate stamped on it. A null here would mean the
        // event was built without one, which is a defect upstream - better loud than a row that claims an
        // unknown version.
        entity.setAggregateVersion(Objects.requireNonNull(event.getAggregateVersionInteger(),
                "aggregateVersion of " + event.getEventType() + " is null"));
        em.merge(entity);
    }

}

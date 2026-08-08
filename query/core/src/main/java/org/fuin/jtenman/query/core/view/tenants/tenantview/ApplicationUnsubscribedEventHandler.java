package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.EventHandler;
import org.fuin.ddd4j.core.EventType;
import org.fuin.jtenman.shared.domain.tenants.ApplicationUnsubscribedEvent;

/**
 * Removes the subscription row, so the application stops seeing the tenant.
 */
public class ApplicationUnsubscribedEventHandler implements EventHandler<ApplicationUnsubscribedEvent> {

    @Override
    public EventType getEventType() {
        return ApplicationUnsubscribedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(final EntityManager em, final ApplicationUnsubscribedEvent event) {
        final String realm = TenantReadModel.realmOf(event.getEntityIdPath());
        final String id = TenantReadModel.subscriptionId(realm, event.getApplication().asBaseType());
        final TenantApplicationEntity entity = em.find(TenantApplicationEntity.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

}

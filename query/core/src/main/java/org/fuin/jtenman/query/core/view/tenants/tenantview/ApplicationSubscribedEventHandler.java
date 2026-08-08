package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.EventHandler;
import org.fuin.ddd4j.core.EventType;
import org.fuin.jtenman.shared.domain.tenants.ApplicationSubscribedEvent;

/**
 * Adds the subscription row that makes the tenant visible to one application.
 */
public class ApplicationSubscribedEventHandler implements EventHandler<ApplicationSubscribedEvent> {

    @Override
    public EventType getEventType() {
        return ApplicationSubscribedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(final EntityManager em, final ApplicationSubscribedEvent event) {
        final String realm = TenantReadModel.realmOf(event.getEntityIdPath());
        final String application = event.getApplication().asBaseType();
        final TenantApplicationEntity entity = new TenantApplicationEntity();
        entity.setId(TenantReadModel.subscriptionId(realm, application));
        entity.setRealm(realm);
        entity.setApplication(application);
        em.merge(entity);
    }

}

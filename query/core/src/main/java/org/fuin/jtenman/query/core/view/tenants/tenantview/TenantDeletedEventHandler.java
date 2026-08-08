package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.EventHandler;
import org.fuin.ddd4j.core.EventType;
import org.fuin.jtenman.shared.domain.tenants.TenantDeletedEvent;

/**
 * Removes the tenant and all of its subscriptions from the read model.
 * <p>
 * Both have to go. Leaving the subscription rows behind would keep the tenant in an application's list
 * after its realm was deleted, which is the one thing the list must never say.
 */
public class TenantDeletedEventHandler implements EventHandler<TenantDeletedEvent> {

    @Override
    public EventType getEventType() {
        return TenantDeletedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(final EntityManager em, final TenantDeletedEvent event) {
        final String realm = TenantReadModel.realmOf(event.getEntityIdPath());
        em.createQuery("DELETE FROM TenantApplicationEntity e WHERE e.realm = :realm")
                .setParameter("realm", realm)
                .executeUpdate();
        final TenantEntity entity = em.find(TenantEntity.class, realm);
        if (entity != null) {
            em.remove(entity);
        }
    }

}

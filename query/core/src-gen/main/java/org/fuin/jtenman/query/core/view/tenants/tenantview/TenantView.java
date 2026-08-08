package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Set;
import org.fuin.cqrs4j.core.View;
import org.fuin.cqrs4j.esc.JpaEventDispatcher;
import org.fuin.cqrs4j.esc.SimpleJpaEventDispatcher;
import org.fuin.ddd4j.core.Event;
import org.fuin.ddd4j.core.EventType;
import org.fuin.jtenman.shared.domain.tenants.ApplicationSubscribedEvent;
import org.fuin.jtenman.shared.domain.tenants.ApplicationUnsubscribedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantDeletedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantRegisteredEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantResumedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantSuspendedEvent;
import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * View with the list of Tenant. Implements {@link View} and is discovered by the query
 * runtime as a bean. Fully generated - regenerated on every build.
 */
@ThreadSafe
@Component(TenantView.BEAN_NAME)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TenantView implements View {

    /** Unique name of the view / projection. */
    public static final String NAME = "spring-qry-tenant";

    /** Name of the bean. */
    public static final String BEAN_NAME = "TenantView";

    private final EntityManager em;

    private final JpaEventDispatcher eventDispatcher;

    /**
     * Constructor with the injected entity manager.
     *
     * @param em Entity manager used to store the read model.
     */
    public TenantView(final EntityManager em) {
        this.em = em;
        this.eventDispatcher = new SimpleJpaEventDispatcher(
            new TenantRegisteredEventHandler(),
            new ApplicationSubscribedEventHandler(),
            new ApplicationUnsubscribedEventHandler(),
            new TenantSuspendedEventHandler(),
            new TenantResumedEventHandler(),
            new TenantDeletedEventHandler()
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getBeanName() {
        return BEAN_NAME;
    }

    @Override
    public Class<? extends View> getBeanClass() {
        return TenantView.class;
    }

    @Override
    public Set<EventType> getEventTypes() {
        return Set.of(TenantRegisteredEvent.EVENT_TYPE, ApplicationSubscribedEvent.EVENT_TYPE, ApplicationUnsubscribedEvent.EVENT_TYPE, TenantSuspendedEvent.EVENT_TYPE, TenantResumedEvent.EVENT_TYPE, TenantDeletedEvent.EVENT_TYPE);
    }

    @Override
    public String getCron() {
        return "* * * * * *";
    }

    @Override
    public void handleEvents(final List<Event> events) {
        eventDispatcher.dispatchEvents(em, events);
    }

}

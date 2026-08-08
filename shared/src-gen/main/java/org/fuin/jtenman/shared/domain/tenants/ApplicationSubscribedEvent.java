package org.fuin.jtenman.shared.domain.tenants;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.jackson.AbstractDomainEvent;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * A tenant was subscribed to an application; the application's Keycloak client and audience mapper were created in its realm.
 */
@HasSerializedDataTypeConstant
public final class ApplicationSubscribedEvent extends AbstractDomainEvent<TenantRealmId> {

    @Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the event. */
    public static final EventType EVENT_TYPE = new EventType("ApplicationSubscribedEvent");
    
    /**
     * Type used to look up the serializer and deserializer. The event store registry is built
     * by scanning for the annotation above, so without this constant the type is unknown at
     * runtime and neither storing nor reading it back works.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    
    @NotNull
    @SuppressWarnings("NullAway.Init")
    private ApplicationId application;
    

    /**
     * Protected default constructor for deserialization and the builder.
     */
    @SuppressWarnings("NullAway.Init")
    protected ApplicationSubscribedEvent() {
        super();
    }
    
    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns: The application to grant access to.
     *
     * @return Current value.
     */
    public ApplicationId getApplication() {
        return application;
    }
    

    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Subscribed to application '${application}'",
            new KeyValue("entityIdPath", getEntityIdPath())
            , new KeyValue("application", application)
        ));
    }
    
    /**
     * Creates a new builder instance.
     *
     * @return New builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builds an instance of the outer class.
     */
    public static final class Builder extends AbstractDomainEvent.Builder<TenantRealmId, ApplicationSubscribedEvent, Builder> {
    
        private ApplicationSubscribedEvent delegate;
    
        private Builder() {
            super(new ApplicationSubscribedEvent());
            delegate = delegate();
        }
    
        /**
         * Sets: The application to grant access to.
         *
         * @param application Value to set.
         * @return This builder.
         */
        public Builder application(final ApplicationId application) {
            Contract.requireArgNotNull("application", application);
            delegate.application = application;
            return this;
        }
        
    
        /**
         * Creates the event and clears the builder.
         *
         * @return New instance.
         */
        public ApplicationSubscribedEvent build() {
            ensureBuildableAbstractDomainEvent();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
        	ensureNotNull("application", delegate.application);
            
            final ApplicationSubscribedEvent result = delegate;
            delegate = new ApplicationSubscribedEvent();
            resetAbstractDomainEvent(delegate);
            return result;
        }
    
    }
}


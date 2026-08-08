package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.ReturnFromExileEvent;
import org.fuin.ddd4j.jackson.AbstractDomainEvent;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * A suspended tenant was resumed and its realm enabled again.
 */
@HasSerializedDataTypeConstant
public final class TenantResumedEvent extends AbstractDomainEvent<TenantRealmId> implements ReturnFromExileEvent {

    @Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the event. */
    public static final EventType EVENT_TYPE = new EventType("TenantResumedEvent");
    
    /**
     * Type used to look up the serializer and deserializer. The event store registry is built
     * by scanning for the annotation above, so without this constant the type is unknown at
     * runtime and neither storing nor reading it back works.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    

    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }


    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Resumed tenant",
            new KeyValue("entityIdPath", getEntityIdPath())
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
    public static final class Builder extends AbstractDomainEvent.Builder<TenantRealmId, TenantResumedEvent, Builder> {
    
        private TenantResumedEvent delegate;
    
        private Builder() {
            super(new TenantResumedEvent());
            delegate = delegate();
        }
    
    
        /**
         * Creates the event and clears the builder.
         *
         * @return New instance.
         */
        public TenantResumedEvent build() {
            ensureBuildableAbstractDomainEvent();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
            
            final TenantResumedEvent result = delegate;
            delegate = new TenantResumedEvent();
            resetAbstractDomainEvent(delegate);
            return result;
        }
    
    }
}


package org.fuin.jtenman.shared.domain.tenants;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.ExodusEvent;
import org.fuin.ddd4j.jackson.AbstractDomainEvent;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * A tenant was deleted for good: its realm, and with it every user and every personal detail the realm held, is gone from Keycloak. <p> This is the event that makes erasure possible. Everything jtenman keeps about people is an opaque subject id, so once the realm is gone those ids resolve to nobody - the history of what was provisioned survives without the personal data it once pointed at.
 */
@HasSerializedDataTypeConstant
public final class TenantDeletedEvent extends AbstractDomainEvent<TenantRealmId> implements ExodusEvent {

    @Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the event. */
    public static final EventType EVENT_TYPE = new EventType("TenantDeletedEvent");
    
    /**
     * Type used to look up the serializer and deserializer. The event store registry is built
     * by scanning for the annotation above, so without this constant the type is unknown at
     * runtime and neither storing nor reading it back works.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    
    @NotNull
    @SuppressWarnings("NullAway.Init")
    private SuspensionReason reason;
    

    /**
     * Protected default constructor for deserialization and the builder.
     */
    @SuppressWarnings("NullAway.Init")
    protected TenantDeletedEvent() {
        super();
    }
    
    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns: Why the tenant was deleted, for whatever audit trail is kept outside jtenman.
     *
     * @return Current value.
     */
    public SuspensionReason getReason() {
        return reason;
    }
    

    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Deleted tenant",
            new KeyValue("entityIdPath", getEntityIdPath())
            , new KeyValue("reason", reason)
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
    public static final class Builder extends AbstractDomainEvent.Builder<TenantRealmId, TenantDeletedEvent, Builder> {
    
        private TenantDeletedEvent delegate;
    
        private Builder() {
            super(new TenantDeletedEvent());
            delegate = delegate();
        }
    
        /**
         * Sets: Why the tenant was deleted, for whatever audit trail is kept outside jtenman.
         *
         * @param reason Value to set.
         * @return This builder.
         */
        public Builder reason(final SuspensionReason reason) {
            Contract.requireArgNotNull("reason", reason);
            delegate.reason = reason;
            return this;
        }
        
    
        /**
         * Creates the event and clears the builder.
         *
         * @return New instance.
         */
        public TenantDeletedEvent build() {
            ensureBuildableAbstractDomainEvent();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
        	ensureNotNull("reason", delegate.reason);
            
            final TenantDeletedEvent result = delegate;
            delegate = new TenantDeletedEvent();
            resetAbstractDomainEvent(delegate);
            return result;
        }
    
    }
}


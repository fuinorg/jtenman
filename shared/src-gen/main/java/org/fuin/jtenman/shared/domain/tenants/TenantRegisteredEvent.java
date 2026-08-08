package org.fuin.jtenman.shared.domain.tenants;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.ddd4j.core.GenesisEvent;
import org.fuin.ddd4j.jackson.AbstractDomainEvent;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * A tenant was registered and its realm created.
 */
@HasSerializedDataTypeConstant
public final class TenantRegisteredEvent extends AbstractDomainEvent<TenantRealmId> implements GenesisEvent {

    @Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the event. */
    public static final EventType EVENT_TYPE = new EventType("TenantRegisteredEvent");
    
    /**
     * Type used to look up the serializer and deserializer. The event store registry is built
     * by scanning for the annotation above, so without this constant the type is unknown at
     * runtime and neither storing nor reading it back works.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    
    @NotNull
    @SuppressWarnings("NullAway.Init")
    private IssuerUri issuerUri;
    

    /**
     * Protected default constructor for deserialization and the builder.
     */
    @SuppressWarnings("NullAway.Init")
    protected TenantRegisteredEvent() {
        super();
    }
    
    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns: Issuer URI of the newly created realm.
     *
     * @return Current value.
     */
    public IssuerUri getIssuerUri() {
        return issuerUri;
    }
    

    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Registered tenant '${issuerUri}'",
            new KeyValue("entityIdPath", getEntityIdPath())
            , new KeyValue("issuerUri", issuerUri)
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
    public static final class Builder extends AbstractDomainEvent.Builder<TenantRealmId, TenantRegisteredEvent, Builder> {
    
        private TenantRegisteredEvent delegate;
    
        private Builder() {
            super(new TenantRegisteredEvent());
            delegate = delegate();
        }
    
        /**
         * Sets: Issuer URI of the newly created realm.
         *
         * @param issuerUri Value to set.
         * @return This builder.
         */
        public Builder issuerUri(final IssuerUri issuerUri) {
            Contract.requireArgNotNull("issuerUri", issuerUri);
            delegate.issuerUri = issuerUri;
            return this;
        }
        
    
        /**
         * Creates the event and clears the builder.
         *
         * @return New instance.
         */
        public TenantRegisteredEvent build() {
            ensureBuildableAbstractDomainEvent();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
        	ensureNotNull("issuerUri", delegate.issuerUri);
            
            final TenantRegisteredEvent result = delegate;
            delegate = new TenantRegisteredEvent();
            resetAbstractDomainEvent(delegate);
            return result;
        }
    
    }
}


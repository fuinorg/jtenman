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
 * A person was invited to administer the tenant's realm. Records who, by subject id - never the email address the invitation was sent to.
 */
@HasSerializedDataTypeConstant
public final class AdministratorInvitedEvent extends AbstractDomainEvent<TenantRealmId> {

    @Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the event. */
    public static final EventType EVENT_TYPE = new EventType("AdministratorInvitedEvent");
    
    /**
     * Type used to look up the serializer and deserializer. The event store registry is built
     * by scanning for the annotation above, so without this constant the type is unknown at
     * runtime and neither storing nor reading it back works.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    
    @NotNull
    @SuppressWarnings("NullAway.Init")
    private SubjectId subjectId;
    

    /**
     * Protected default constructor for deserialization and the builder.
     */
    @SuppressWarnings("NullAway.Init")
    protected AdministratorInvitedEvent() {
        super();
    }
    
    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns: The person now able to administer the tenant's realm.
     *
     * @return Current value.
     */
    public SubjectId getSubjectId() {
        return subjectId;
    }
    

    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Invited an administrator for the tenant",
            new KeyValue("entityIdPath", getEntityIdPath())
            , new KeyValue("subjectId", subjectId)
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
    public static final class Builder extends AbstractDomainEvent.Builder<TenantRealmId, AdministratorInvitedEvent, Builder> {
    
        private AdministratorInvitedEvent delegate;
    
        private Builder() {
            super(new AdministratorInvitedEvent());
            delegate = delegate();
        }
    
        /**
         * Sets: The person now able to administer the tenant's realm.
         *
         * @param subjectId Value to set.
         * @return This builder.
         */
        public Builder subjectId(final SubjectId subjectId) {
            Contract.requireArgNotNull("subjectId", subjectId);
            delegate.subjectId = subjectId;
            return this;
        }
        
    
        /**
         * Creates the event and clears the builder.
         *
         * @return New instance.
         */
        public AdministratorInvitedEvent build() {
            ensureBuildableAbstractDomainEvent();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
        	ensureNotNull("subjectId", delegate.subjectId);
            
            final AdministratorInvitedEvent result = delegate;
            delegate = new AdministratorInvitedEvent();
            resetAbstractDomainEvent(delegate);
            return result;
        }
    
    }
}


package org.fuin.jtenman.command.api.tenants;

import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.fuin.cqrs4j.jackson.AbstractAggregateCommand;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * Resume a suspended tenant.
 */
@HasSerializedDataTypeConstant
public final class ResumeTenantCommand extends AbstractAggregateCommand<TenantRealmId, TenantRealmId> {

	@Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the command. */
    public static final EventType EVENT_TYPE = new EventType("ResumeTenantCommand");
    
    /**
     * Type used to look up the serializer and deserializer. The registry is built by scanning
     * for the annotation above, so without this constant the command cannot be deserialized
     * when it arrives at the command endpoint.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    

    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }


    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Resume tenant",
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
    public static final class Builder extends AbstractAggregateCommand.Builder<TenantRealmId, TenantRealmId, ResumeTenantCommand, Builder> {
    
        private ResumeTenantCommand delegate;
    
        private Builder() {
            super(new ResumeTenantCommand());
            delegate = delegate();
        }
    
    
        /**
         * Creates the command and clears the builder.
         *
         * @return New instance.
         */
        public ResumeTenantCommand build() {
            ensureBuildableAbstractAggregateCommand();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
            
            final ResumeTenantCommand result = delegate;
            delegate = new ResumeTenantCommand();
            resetAbstractAggregateCommand(delegate);
            return result;
        }
    
    }
}

package org.fuin.jtenman.command.api.tenants;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.fuin.cqrs4j.jackson.AbstractAggregateCommand;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.jtenman.shared.domain.tenants.SuspensionReason;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * Suspend a tenant and disable its realm.
 */
@HasSerializedDataTypeConstant
public final class SuspendTenantCommand extends AbstractAggregateCommand<TenantRealmId, TenantRealmId> {

	@Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the command. */
    public static final EventType EVENT_TYPE = new EventType("SuspendTenantCommand");
    
    /**
     * Type used to look up the serializer and deserializer. The registry is built by scanning
     * for the annotation above, so without this constant the command cannot be deserialized
     * when it arrives at the command endpoint.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    
    @NotNull
    @SuppressWarnings("NullAway.Init")
    private SuspensionReason reason;
    

    /**
     * Protected default constructor for deserialization and the builder.
     */
    @SuppressWarnings("NullAway.Init")
    protected SuspendTenantCommand() {
        super();
    }
    
    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns: Why the tenant is being suspended.
     *
     * @return Current value.
     */
    public SuspensionReason getReason() {
        return reason;
    }
    

    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Suspend tenant",
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
    public static final class Builder extends AbstractAggregateCommand.Builder<TenantRealmId, TenantRealmId, SuspendTenantCommand, Builder> {
    
        private SuspendTenantCommand delegate;
    
        private Builder() {
            super(new SuspendTenantCommand());
            delegate = delegate();
        }
    
        /**
         * Sets: Why the tenant is being suspended.
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
         * Creates the command and clears the builder.
         *
         * @return New instance.
         */
        public SuspendTenantCommand build() {
            ensureBuildableAbstractAggregateCommand();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
        	ensureNotNull("reason", delegate.reason);
            
            final SuspendTenantCommand result = delegate;
            delegate = new SuspendTenantCommand();
            resetAbstractAggregateCommand(delegate);
            return result;
        }
    
    }
}

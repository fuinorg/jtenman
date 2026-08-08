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
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * Grant a tenant access to an application.
 */
@HasSerializedDataTypeConstant
public final class SubscribeApplicationCommand extends AbstractAggregateCommand<TenantRealmId, TenantRealmId> {

	@Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the command. */
    public static final EventType EVENT_TYPE = new EventType("SubscribeApplicationCommand");
    
    /**
     * Type used to look up the serializer and deserializer. The registry is built by scanning
     * for the annotation above, so without this constant the command cannot be deserialized
     * when it arrives at the command endpoint.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    
    @NotNull
    @SuppressWarnings("NullAway.Init")
    private ApplicationId application;
    

    /**
     * Protected default constructor for deserialization and the builder.
     */
    @SuppressWarnings("NullAway.Init")
    protected SubscribeApplicationCommand() {
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
        return Objects.requireNonNull(KeyValueEL.replace("Subscribe to application '${application}'",
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
    public static final class Builder extends AbstractAggregateCommand.Builder<TenantRealmId, TenantRealmId, SubscribeApplicationCommand, Builder> {
    
        private SubscribeApplicationCommand delegate;
    
        private Builder() {
            super(new SubscribeApplicationCommand());
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
         * Creates the command and clears the builder.
         *
         * @return New instance.
         */
        public SubscribeApplicationCommand build() {
            ensureBuildableAbstractAggregateCommand();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
        	ensureNotNull("application", delegate.application);
            
            final SubscribeApplicationCommand result = delegate;
            delegate = new SubscribeApplicationCommand();
            resetAbstractAggregateCommand(delegate);
            return result;
        }
    
    }
}

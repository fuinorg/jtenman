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
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * Register a new tenant and create its realm.
 */
@HasSerializedDataTypeConstant
public final class RegisterTenantCommand extends AbstractAggregateCommand<TenantRealmId, TenantRealmId> {

	@Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the command. */
    public static final EventType EVENT_TYPE = new EventType("RegisterTenantCommand");
    
    /**
     * Type used to look up the serializer and deserializer. The registry is built by scanning
     * for the annotation above, so without this constant the command cannot be deserialized
     * when it arrives at the command endpoint.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    
    @NotNull
    @SuppressWarnings("NullAway.Init")
    private RealmName realm;
    

    /**
     * Protected default constructor for deserialization and the builder.
     */
    @SuppressWarnings("NullAway.Init")
    protected RegisterTenantCommand() {
        super();
    }
    
    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns: Name of the realm to create.
     *
     * @return Current value.
     */
    public RealmName getRealm() {
        return realm;
    }
    

    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Register tenant '${realm}'",
            new KeyValue("entityIdPath", getEntityIdPath())
            , new KeyValue("realm", realm)
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
    public static final class Builder extends AbstractAggregateCommand.Builder<TenantRealmId, TenantRealmId, RegisterTenantCommand, Builder> {
    
        private RegisterTenantCommand delegate;
    
        private Builder() {
            super(new RegisterTenantCommand());
            delegate = delegate();
        }
    
        /**
         * Sets: Name of the realm to create.
         *
         * @param realm Value to set.
         * @return This builder.
         */
        public Builder realm(final RealmName realm) {
            Contract.requireArgNotNull("realm", realm);
            delegate.realm = realm;
            return this;
        }
        
    
        /**
         * Creates the command and clears the builder.
         *
         * @return New instance.
         */
        public RegisterTenantCommand build() {
            ensureBuildableAbstractAggregateCommand();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
        	ensureNotNull("realm", delegate.realm);
            
            final RegisterTenantCommand result = delegate;
            delegate = new RegisterTenantCommand();
            resetAbstractAggregateCommand(delegate);
            return result;
        }
    
    }
}

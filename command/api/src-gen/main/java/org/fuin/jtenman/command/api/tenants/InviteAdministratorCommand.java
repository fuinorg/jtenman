package org.fuin.jtenman.command.api.tenants;

import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.Objects;
import org.fuin.cqrs4j.jackson.AbstractAggregateCommand;
import org.fuin.ddd4j.core.EventId;
import org.fuin.ddd4j.core.EventType;
import org.fuin.dsl.cqrs.common.basics.EmailAddress;
import org.fuin.esc.api.HasSerializedDataTypeConstant;
import org.fuin.esc.api.SerializedDataType;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * Invite a person to administer the tenant's realm.
 */
@HasSerializedDataTypeConstant
public final class InviteAdministratorCommand extends AbstractAggregateCommand<TenantRealmId, TenantRealmId> {

	@Serial
    private static final long serialVersionUID = 1000L;

    /** Unique name used to store the command. */
    public static final EventType EVENT_TYPE = new EventType("InviteAdministratorCommand");
    
    /**
     * Type used to look up the serializer and deserializer. The registry is built by scanning
     * for the annotation above, so without this constant the command cannot be deserialized
     * when it arrives at the command endpoint.
     */
    public static final SerializedDataType SER_TYPE = new SerializedDataType(EVENT_TYPE.asBaseType());
    
    @NotNull
    @SuppressWarnings("NullAway.Init")
    private EmailAddress email;
    

    /**
     * Protected default constructor for deserialization and the builder.
     */
    @SuppressWarnings("NullAway.Init")
    protected InviteAdministratorCommand() {
        super();
    }
    
    @Override
    public EventType getEventType() {
        return EVENT_TYPE;
    }

    /**
     * Returns: Where to send the invitation. Used to send it and then forgotten - only the resulting subject id becomes part of the event stream.
     *
     * @return Current value.
     */
    public EmailAddress getEmail() {
        return email;
    }
    

    @Override
    public String toString() {
        return Objects.requireNonNull(KeyValueEL.replace("Invite an administrator for the tenant",
            new KeyValue("entityIdPath", getEntityIdPath())
            , new KeyValue("email", email)
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
    public static final class Builder extends AbstractAggregateCommand.Builder<TenantRealmId, TenantRealmId, InviteAdministratorCommand, Builder> {
    
        private InviteAdministratorCommand delegate;
    
        private Builder() {
            super(new InviteAdministratorCommand());
            delegate = delegate();
        }
    
        /**
         * Sets: Where to send the invitation. Used to send it and then forgotten - only the resulting subject id becomes part of the event stream.
         *
         * @param email Value to set.
         * @return This builder.
         */
        public Builder email(final EmailAddress email) {
            Contract.requireArgNotNull("email", email);
            delegate.email = email;
            return this;
        }
        
    
        /**
         * Creates the command and clears the builder.
         *
         * @return New instance.
         */
        public InviteAdministratorCommand build() {
            ensureBuildableAbstractAggregateCommand();
            if (delegate.getEventId() == null) {
                this.eventId(new EventId());
            }
            if (delegate.getEventTimestamp() == null) {
                this.timestamp(ZonedDateTime.now());
            }
            
        	ensureNotNull("email", delegate.email);
            
            final InviteAdministratorCommand result = delegate;
            delegate = new InviteAdministratorCommand();
            resetAbstractAggregateCommand(delegate);
            return result;
        }
    
    }
}

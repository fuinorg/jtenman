package org.fuin.jtenman.command.core.handler.tenants;

import org.fuin.ddd4j.core.AggregateDeletedException;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.Result;
import org.fuin.cqrs4j.esc.AuditedRepository;
import org.fuin.cqrs4j.jackson.SimpleResult;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.dsl.cqrs.common.exceptions.EntityInStateDeletedException;
import org.fuin.jtenman.command.api.tenants.SubscribeApplicationCommand;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.SubscribeApplicationService;
import org.fuin.jtenman.command.core.domain.tenants.Tenant;
import org.fuin.jtenman.command.core.domain.tenants.TenantRepository;
import org.fuin.jtenman.shared.domain.tenants.ApplicationAlreadySubscribedException;
import org.fuin.jtenman.shared.domain.tenants.UnknownApplicationException;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Objects;

/**
 * Grants a tenant access to an application, from a {@link SubscribeApplicationCommand}.
 */
@ThreadSafe
public class SubscribeApplicationCommandHandler implements CommandHandler<SubscribeApplicationCommand, Result<Void>> {

    private final TenantRepository repository;

    private final SubscribeApplicationService service;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param repository Repository the tenant is loaded from and stored in.
     * @param service Collaborator the aggregate method needs.
     */
    public SubscribeApplicationCommandHandler(final TenantRepository repository, final SubscribeApplicationService service) {
        this.repository = Objects.requireNonNull(repository, "repository==null");
        this.service = Objects.requireNonNull(service, "service==null");
    }

    @Override
    public Class<SubscribeApplicationCommand> getCommandType() {
        return SubscribeApplicationCommand.class;
    }

    @Override
    public Result<Void> handle(final CommandExecutionContext context, final SubscribeApplicationCommand cmd)
            throws CommandExecutionFailedException {
        try {
            final Tenant tenant = repository.read(cmd.getAggregateRootId());
            tenant.subscribeApplication(cmd.getApplication(), service);
            AuditedRepository.update(repository, tenant, context);
            return SimpleResult.ok();
        } catch (final AggregateNotFoundException | AggregateDeletedException ex) {
            // The tenant is gone. That is an answer the caller can act on, not a server fault.
            return new SimpleResult(new EntityInStateDeletedException(new EntityIdPath(cmd.getAggregateRootId())));
        } catch (final EntityInStateDeletedException ex) {
            // Survived its own deletion - see Tenant.requireNotDeleted().
            return new SimpleResult(ex);
        } catch (UnknownApplicationException | ApplicationAlreadySubscribedException ex) {
            // A rule the model declares was violated. That is an outcome of the operation, not a failure of
            // it: the caller gets the code and the message rather than a stack trace.
            return new SimpleResult(ex);        } catch (final Exception ex) {
            // Anything else is infrastructure - the caller cannot act on it.
            throw new CommandExecutionFailedException(ex);
        }
    }

}

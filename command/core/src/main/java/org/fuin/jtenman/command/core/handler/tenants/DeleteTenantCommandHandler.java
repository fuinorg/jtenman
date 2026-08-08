package org.fuin.jtenman.command.core.handler.tenants;

import org.fuin.ddd4j.core.AggregateDeletedException;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.jtenman.shared.domain.tenants.TenantAlreadyDeletedException;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.Result;
import org.fuin.cqrs4j.jackson.SimpleResult;
import org.fuin.jtenman.command.api.tenants.DeleteTenantCommand;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.DeleteTenantService;
import org.fuin.jtenman.command.core.domain.tenants.Tenant;
import org.fuin.jtenman.command.core.domain.tenants.TenantRepository;
import org.fuin.jtenman.shared.domain.tenants.TenantNotSuspendedException;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Objects;

/**
 * Deletes a suspended tenant for good, from a {@link DeleteTenantCommand}. This is the erasure path - see steering/security.md.
 */
@ThreadSafe
public class DeleteTenantCommandHandler implements CommandHandler<DeleteTenantCommand, Result<Void>> {

    private final TenantRepository repository;

    private final DeleteTenantService service;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param repository Repository the tenant is loaded from and stored in.
     * @param service Collaborator the aggregate method needs.
     */
    public DeleteTenantCommandHandler(final TenantRepository repository, final DeleteTenantService service) {
        this.repository = Objects.requireNonNull(repository, "repository==null");
        this.service = Objects.requireNonNull(service, "service==null");
    }

    @Override
    public Class<DeleteTenantCommand> getCommandType() {
        return DeleteTenantCommand.class;
    }

    @Override
    public Result<Void> handle(final CommandExecutionContext context, final DeleteTenantCommand cmd)
            throws CommandExecutionFailedException {
        try {
            final Tenant tenant = repository.read(cmd.getAggregateRootId());
            tenant.deleteTenant(cmd.getReason(), service);
            repository.update(tenant);
            // Record the deletion first, then remove the aggregate. Without this the realm is gone from
            // Keycloak while the tenant still answers commands, and re-registering the same realm fails
            // forever with "already registered" against a history nothing can reach.
            //
            // delete(), not purge(): the stream id is the realm name, and purge tombstones it so that
            // name could never be used again - see steering/security.md. The personal data was never in
            // the stream, only opaque subject ids, so the soft delete erases nothing that matters and
            // keeps the provisioning history.
            repository.delete(cmd.getAggregateRootId(), null);
            return SimpleResult.ok();
        } catch (final AggregateNotFoundException | AggregateDeletedException ex) {
            // The tenant is gone. That is an answer the caller can act on, not a server fault.
            return new SimpleResult(new TenantAlreadyDeletedException(cmd.getAggregateRootId().asBaseType()));
        } catch (final TenantAlreadyDeletedException ex) {
            // Survived its own deletion - see Tenant.requireNotDeleted().
            return new SimpleResult(ex);
        } catch (TenantNotSuspendedException ex) {
            // A rule the model declares was violated. That is an outcome of the operation, not a failure of
            // it: the caller gets the code and the message rather than a stack trace.
            return new SimpleResult(ex);        } catch (final Exception ex) {
            // Anything else is infrastructure - the caller cannot act on it.
            throw new CommandExecutionFailedException(ex);
        }
    }

}

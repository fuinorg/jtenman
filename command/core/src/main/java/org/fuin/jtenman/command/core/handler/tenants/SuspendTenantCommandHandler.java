package org.fuin.jtenman.command.core.handler.tenants;

import org.fuin.ddd4j.core.AggregateDeletedException;
import org.fuin.ddd4j.core.AggregateNotFoundException;
import org.fuin.jtenman.shared.domain.tenants.TenantAlreadyDeletedException;
import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.Result;
import org.fuin.cqrs4j.jackson.SimpleResult;
import org.fuin.jtenman.command.api.tenants.SuspendTenantCommand;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.SuspendTenantService;
import org.fuin.jtenman.command.core.domain.tenants.Tenant;
import org.fuin.jtenman.command.core.domain.tenants.TenantRepository;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Objects;

/**
 * Suspends a tenant and disables its realm, from a {@link SuspendTenantCommand}.
 */
@ThreadSafe
public class SuspendTenantCommandHandler implements CommandHandler<SuspendTenantCommand, Result<Void>> {

    private final TenantRepository repository;

    private final SuspendTenantService service;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param repository Repository the tenant is loaded from and stored in.
     * @param service Collaborator the aggregate method needs.
     */
    public SuspendTenantCommandHandler(final TenantRepository repository, final SuspendTenantService service) {
        this.repository = Objects.requireNonNull(repository, "repository==null");
        this.service = Objects.requireNonNull(service, "service==null");
    }

    @Override
    public Class<SuspendTenantCommand> getCommandType() {
        return SuspendTenantCommand.class;
    }

    @Override
    public Result<Void> handle(final CommandExecutionContext context, final SuspendTenantCommand cmd)
            throws CommandExecutionFailedException {
        try {
            final Tenant tenant = repository.read(cmd.getAggregateRootId());
            tenant.suspendTenant(cmd.getReason(), service);
            repository.update(tenant);
            return SimpleResult.ok();
        } catch (final AggregateNotFoundException | AggregateDeletedException ex) {
            // The tenant is gone. That is an answer the caller can act on, not a server fault.
            return new SimpleResult(new TenantAlreadyDeletedException(cmd.getAggregateRootId().asBaseType()));
        } catch (final TenantAlreadyDeletedException ex) {
            // Survived its own deletion - see Tenant.requireNotDeleted().
            return new SimpleResult(ex);
        } catch (final Exception ex) {
            // Anything else is infrastructure - the caller cannot act on it.
            throw new CommandExecutionFailedException(ex);
        }
    }

}

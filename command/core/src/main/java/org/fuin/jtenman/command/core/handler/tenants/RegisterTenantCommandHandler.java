package org.fuin.jtenman.command.core.handler.tenants;

import org.fuin.cqrs4j.core.CommandExecutionContext;
import org.fuin.cqrs4j.core.CommandExecutionFailedException;
import org.fuin.cqrs4j.core.CommandHandler;
import org.fuin.cqrs4j.core.Result;
import org.fuin.cqrs4j.esc.AuditedRepository;
import org.fuin.cqrs4j.jackson.SimpleResult;
import org.fuin.jtenman.command.api.tenants.RegisterTenantCommand;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.RegisterTenantService;
import org.fuin.jtenman.command.core.domain.tenants.Tenant;
import org.fuin.jtenman.command.core.domain.tenants.TenantRepository;
import org.fuin.ddd4j.core.AggregateAlreadyExistsException;
import org.fuin.jtenman.shared.domain.tenants.TenantAlreadyRegisteredException;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Objects;

/**
 * Registers a tenant and creates its realm, from a {@link RegisterTenantCommand}.
 * <p>
 * The identifier is the one the command carries - for a creating command that is the aggregate to
 * create. Taking it from the command rather than generating one here is what lets the caller correlate
 * the result, and what makes a re-delivery of the same command hit the "already registered" check
 * instead of creating a second tenant.
 */
@ThreadSafe
public class RegisterTenantCommandHandler implements CommandHandler<RegisterTenantCommand, Result<Void>> {

    private final TenantRepository repository;

    private final RegisterTenantService service;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param repository Repository the new tenant is stored in.
     * @param service Creates the tenant's realm in Keycloak.
     */
    public RegisterTenantCommandHandler(final TenantRepository repository, final RegisterTenantService service) {
        this.repository = Objects.requireNonNull(repository, "repository==null");
        this.service = Objects.requireNonNull(service, "service==null");
    }

    @Override
    public Class<RegisterTenantCommand> getCommandType() {
        return RegisterTenantCommand.class;
    }

    @Override
    public Result<Void> handle(final CommandExecutionContext context, final RegisterTenantCommand cmd)
            throws CommandExecutionFailedException {
        try {
            final Tenant tenant = new Tenant(cmd.getAggregateRootId(), cmd.getRealm(), service);
            AuditedRepository.add(repository, tenant, context);
            return SimpleResult.ok();
        } catch (final AggregateAlreadyExistsException ex) {
            // The realm was gone from Keycloak but the tenant's history was not, so the domain check
            // passed and the event store refused the append. Report it like any other "already there"
            // outcome rather than as a 500 - the caller can act on it, and it is exactly what a repeated
            // command looks like.
            return new SimpleResult(new TenantAlreadyRegisteredException(cmd.getRealm().asBaseType()));
        } catch (final TenantAlreadyRegisteredException ex) {
            // A rule the model declares was violated. That is an outcome of the operation, not a failure
            // of it: the caller gets the code and the message rather than a stack trace.
            return new SimpleResult(ex);
        } catch (final Exception ex) {
            // Anything else is infrastructure - the caller cannot act on it.
            throw new CommandExecutionFailedException(ex);
        }
    }

}

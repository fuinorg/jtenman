/**
 * Copyright (C) 2026 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
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
            AuditedRepository.update(repository, tenant, context);
            // The aggregate's stream is deliberately NOT deleted here.
            //
            // Deleting it immediately after appending TenantDeletedEvent races the projection: the read
            // model reads the stream asynchronously, so the deletion event would be gone before it was
            // ever consumed - leaving a deleted tenant listed as ACTIVE for every application polling the
            // list, which is exactly the state this system exists to prevent.
            //
            // Nothing is lost by keeping it. The personal data was never in the stream (only opaque
            // subject ids); removeRealm erased it in Keycloak, and what remains is the provisioning
            // history worth auditing. The MustNotBeDeleted rule keeps the aggregate inert.
            //
            // If the streams themselves ever have to go, that is a separate reaper that deletes them once
            // every projection has passed the deletion event - see steering/tech.md - never this handler.
            return SimpleResult.ok();
        } catch (final AggregateNotFoundException | AggregateDeletedException ex) {
            // The tenant is gone. That is an answer the caller can act on, not a server fault.
            return new SimpleResult(new EntityInStateDeletedException(new EntityIdPath(cmd.getAggregateRootId())));
        } catch (final EntityInStateDeletedException ex) {
            // Survived its own deletion - see Tenant.requireNotDeleted().
            return new SimpleResult(ex);
        } catch (TenantNotSuspendedException ex) {
            // A rule the model declares was violated. That is an outcome of the operation, not a failure of
            // it: the caller gets the code and the message rather than a stack trace.
            return new SimpleResult(ex);
        } catch (final Exception ex) {
            // Anything else is infrastructure - the caller cannot act on it.
            throw new CommandExecutionFailedException(ex);
        }
    }

}

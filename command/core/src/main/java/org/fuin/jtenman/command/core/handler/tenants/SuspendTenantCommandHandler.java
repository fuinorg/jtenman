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
            AuditedRepository.update(repository, tenant, context);
            return SimpleResult.ok();
        } catch (final AggregateNotFoundException | AggregateDeletedException ex) {
            // The tenant is gone. That is an answer the caller can act on, not a server fault.
            return new SimpleResult(new EntityInStateDeletedException(new EntityIdPath(cmd.getAggregateRootId())));
        } catch (final EntityInStateDeletedException ex) {
            // Survived its own deletion - see Tenant.requireNotDeleted().
            return new SimpleResult(ex);
        } catch (final Exception ex) {
            // Anything else is infrastructure - the caller cannot act on it.
            throw new CommandExecutionFailedException(ex);
        }
    }

}

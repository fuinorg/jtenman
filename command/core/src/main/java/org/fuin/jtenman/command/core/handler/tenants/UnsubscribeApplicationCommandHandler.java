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
import org.fuin.jtenman.command.api.tenants.UnsubscribeApplicationCommand;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.UnsubscribeApplicationService;
import org.fuin.jtenman.command.core.domain.tenants.Tenant;
import org.fuin.jtenman.command.core.domain.tenants.TenantRepository;
import org.fuin.jtenman.shared.domain.tenants.ApplicationNotSubscribedException;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Objects;

/**
 * Withdraws a tenant's access to an application, from an {@link UnsubscribeApplicationCommand}.
 */
@ThreadSafe
public class UnsubscribeApplicationCommandHandler implements CommandHandler<UnsubscribeApplicationCommand, Result<Void>> {

    private final TenantRepository repository;

    private final UnsubscribeApplicationService service;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param repository Repository the tenant is loaded from and stored in.
     * @param service Collaborator the aggregate method needs.
     */
    public UnsubscribeApplicationCommandHandler(final TenantRepository repository, final UnsubscribeApplicationService service) {
        this.repository = Objects.requireNonNull(repository, "repository==null");
        this.service = Objects.requireNonNull(service, "service==null");
    }

    @Override
    public Class<UnsubscribeApplicationCommand> getCommandType() {
        return UnsubscribeApplicationCommand.class;
    }

    @Override
    public Result<Void> handle(final CommandExecutionContext context, final UnsubscribeApplicationCommand cmd)
            throws CommandExecutionFailedException {
        try {
            final Tenant tenant = repository.read(cmd.getAggregateRootId());
            tenant.unsubscribeApplication(cmd.getApplication(), service);
            AuditedRepository.update(repository, tenant, context);
            return SimpleResult.ok();
        } catch (final AggregateNotFoundException | AggregateDeletedException ex) {
            // The tenant is gone. That is an answer the caller can act on, not a server fault.
            return new SimpleResult(new EntityInStateDeletedException(new EntityIdPath(cmd.getAggregateRootId())));
        } catch (final EntityInStateDeletedException ex) {
            // Survived its own deletion - see Tenant.requireNotDeleted().
            return new SimpleResult(ex);
        } catch (ApplicationNotSubscribedException ex) {
            // A rule the model declares was violated. That is an outcome of the operation, not a failure of
            // it: the caller gets the code and the message rather than a stack trace.
            return new SimpleResult(ex);        } catch (final Exception ex) {
            // Anything else is infrastructure - the caller cannot act on it.
            throw new CommandExecutionFailedException(ex);
        }
    }

}

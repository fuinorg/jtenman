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
package org.fuin.jtenman.starter;

import org.fuin.ddd4j.core.Tenant;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.objects4j.common.Immutable;
import org.fuin.ddd4j.core.TenantRemovedEvent;

import java.util.Objects;

/**
 * A tenant of the replicated list, as announced by {@link TenantAddedEvent} and
 * {@link TenantRemovedEvent}.
 * <p>
 * Deliberately <b>not</b> a {@code JwtTenant}: constructing one performs OpenID Connect discovery
 * against the tenant's realm, and announcing a change to the list must not depend on Keycloak being
 * reachable. A tenant that appears while Keycloak is down still has to reach the listeners - the
 * per-tenant datasource and the projection wiring have nothing to do with tokens - and a tenant that
 * disappears has to reach them <i>especially</i> then, because that is the eviction that makes
 * revocation work.
 * <p>
 * Every listener of both events reads nothing but {@link #getTenantId()}, so there is nothing else to
 * carry.
 *
 * @param tenantId Identifier of the tenant, derived from its realm name.
 */
@Immutable
public record RegisteredTenant(TenantId tenantId) implements Tenant {

    /**
     * Compact constructor validating the mandatory parts.
     */
    public RegisteredTenant {
        Objects.requireNonNull(tenantId, "tenantId==null");
    }

    @Override
    public TenantId getTenantId() {
        return tenantId;
    }

}

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
package org.fuin.jtenman.command.core.domain.tenants;

import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.esc.EventStoreRepository;
import org.fuin.esc.api.EventStore;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;

/**
 * Repository that is capable of storing a {@link Tenant}.
 */
public final class TenantRepository extends EventStoreRepository<TenantRealmId, Tenant> {

    /**
     * Constructor with all mandatory data.
     * 
     * @param eventStore Event store.
     */
    public TenantRepository(final EventStore eventStore) {
        super(eventStore);
    }

    @Override
    public Class<Tenant> getAggregateClass() {
        return Tenant.class;
    }

    @Override
    public final EntityType getAggregateType() {
        return TenantRealmId.TYPE;
    }

    @Override
    public final Tenant create() {
        return new Tenant();
    }

    @Override
    protected final String getIdParamName() {
        return "tenantRealmId";
    }

}

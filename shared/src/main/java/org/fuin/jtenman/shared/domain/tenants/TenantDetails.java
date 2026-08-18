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
package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import org.fuin.dsl.cqrs.common.basics.VersionedEntityIdPath;

/**
 * A read-model row describing one tenant of one application - the answer a consuming application replicates to decide which issuers it trusts.
 */
public final class TenantDetails extends AbstractTenantDetails {

    @Serial
    private static final long serialVersionUID = 1000L;
    
    /**
     * Default constructor.
     */
    @SuppressWarnings("NullAway.Init")
    protected TenantDetails() {
        super();
    }
    
    /**
     * Constructor with all data.
     *
     * @param source Aggregate this row was projected from and the version it reflects.
     * @param realm Name of the tenant's realm.
     * @param issuerUri Issuer URI the tenant's tokens carry.
     * @param status Whether the tenant may currently be used.
     */
    public TenantDetails(final VersionedEntityIdPath source, final RealmName realm, final IssuerUri issuerUri, final TenantStatus status) {
        super(source, realm, issuerUri, status);
    }
    
}

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
package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.cqrs4j.core.EventHandler;
import org.fuin.ddd4j.core.EventType;
import org.fuin.jtenman.shared.domain.tenants.TenantResumedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;

/**
 * Marks the tenant active again.
 */
public class TenantResumedEventHandler implements EventHandler<TenantResumedEvent> {

    @Override
    public EventType getEventType() {
        return TenantResumedEvent.EVENT_TYPE;
    }

    @Override
    public void handle(final EntityManager em, final TenantResumedEvent event) {
        TenantReadModel.setStatus(em, TenantReadModel.realmOf(event.getEntityIdPath()), TenantStatus.ACTIVE);
    }

}

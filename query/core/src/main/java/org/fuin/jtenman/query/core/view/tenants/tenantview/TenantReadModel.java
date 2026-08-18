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
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.fuin.objects4j.common.ThreadSafe;

/**
 * The few things the tenant event handlers all need, in one place rather than copied into six classes.
 */
@ThreadSafe
final class TenantReadModel {

    /** Separates the two parts of a subscription key. Not a legal realm or application character. */
    private static final String KEY_SEPARATOR = ":";

    private TenantReadModel() {
        throw new UnsupportedOperationException("It is not allowed to create an instance of this utility class");
    }

    /**
     * Returns the realm an event belongs to.
     * <p>
     * The realm <em>is</em> the aggregate identifier, so it is read back from the path rather than
     * carried in every event - one value, one source.
     *
     * @param path Entity id path of the event.
     *
     * @return Name of the realm.
     */
    static String realmOf(final EntityIdPath path) {
        return path.last().asString();
    }

    /**
     * Builds the key of a subscription row.
     *
     * @param realm Name of the realm.
     * @param application Identifier of the application.
     *
     * @return Key that is unique across tenants and applications.
     */
    static String subscriptionId(final String realm, final String application) {
        return realm + KEY_SEPARATOR + application;
    }

    /**
     * Sets the status of a tenant, if the tenant is known.
     *
     * @param em Entity manager.
     * @param realm Name of the realm.
     * @param status Status to set.
     */
    static void setStatus(final EntityManager em, final String realm, final TenantStatus status) {
        final TenantEntity entity = em.find(TenantEntity.class, realm);
        if (entity != null) {
            entity.setStatus(status.name());
            em.merge(entity);
        }
    }

}

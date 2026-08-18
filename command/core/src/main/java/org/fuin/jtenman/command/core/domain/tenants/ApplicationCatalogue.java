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

import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.objects4j.common.ThreadSafe;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The applications that exist in this system, and what each one is called in Keycloak.
 * <p>
 * This is deliberately <b>configuration, not domain state</b>: which applications exist changes rarely
 * and a restart of jtenman - and of nothing else - is an acceptable price for changing it. What is domain
 * state is which applications a given tenant uses, and that lives in the {@link Tenant} aggregate.
 * <p>
 * The split matters for the event stream too. Only the {@link ApplicationId} is ever recorded; the client
 * id and audience are deployment details that may legitimately change, and writing them into an immutable
 * event would freeze a deployment decision into history.
 */
@ThreadSafe
public class ApplicationCatalogue {

    private final Map<String, Entry> byId;

    /**
     * Constructor with the configured applications.
     *
     * @param entries Applications of the system, in configuration order.
     */
    public ApplicationCatalogue(final List<Entry> entries) {
        Objects.requireNonNull(entries, "entries==null");
        final Map<String, Entry> map = new LinkedHashMap<>();
        for (final Entry entry : entries) {
            map.put(entry.id(), entry);
        }
        this.byId = Map.copyOf(map);
    }

    /**
     * Determines if an identifier is part of the catalogue.
     *
     * @param application Identifier to check.
     *
     * @return TRUE if the catalogue contains it.
     */
    public boolean contains(final ApplicationId application) {
        Objects.requireNonNull(application, "application==null");
        return byId.containsKey(application.asBaseType());
    }

    /**
     * Returns the entry for an identifier.
     *
     * @param application Identifier to look up.
     *
     * @return The entry, or empty if the catalogue does not contain it.
     */
    public Optional<Entry> find(final ApplicationId application) {
        Objects.requireNonNull(application, "application==null");
        return Optional.ofNullable(byId.get(application.asBaseType()));
    }

    /**
     * Returns the entry for an identifier or fails.
     *
     * @param application Identifier to look up.
     *
     * @return The entry, never null.
     *
     * @throws IllegalArgumentException The catalogue does not contain the identifier.
     */
    public Entry require(final ApplicationId application) {
        return find(application).orElseThrow(() -> new IllegalArgumentException(
                "Unknown application '" + application.asBaseType() + "' - configured are: " + byId.keySet()));
    }

    /**
     * One application of the system.
     * <p>
     * The two role lists belong here for the same reason the client id and the audience do: they change
     * with a release of the application, not with anything a tenant does. That also means they must
     * <b>never</b> be written into {@code ApplicationSubscribedEvent} - only the {@link ApplicationId} is
     * ever recorded, because an immutable event carrying a deployment detail freezes it into history.
     *
     * @param id Identifier used in commands and in the tenant list.
     * @param displayName Human readable name.
     * @param clientId Keycloak client created in a subscribing tenant's realm.
     * @param audience Value the client's audience mapper emits, which the application validates.
     * @param realmRoles The application's own roles, created in the tenant's realm and carried by the
     *                   administrators group. Realm roles, not client roles: an application's JWT
     *                   converter reads {@code realm_access.roles}, and a client role is invisible there -
     *                   the check then fails with nothing wrong in Keycloak's UI to point at.
     * @param realmManagementRoles Client roles of the {@code realm-management} client the administrators
     *                             group needs so that the application can administer logins under the
     *                             caller's own token. These are client roles because they belong to
     *                             Keycloak's own admin API rather than to the application.
     */
    public record Entry(String id, @Nullable String displayName, String clientId, String audience,
                        List<String> realmRoles, List<String> realmManagementRoles) {

        /**
         * Compact constructor validating the mandatory parts and defaulting the role lists.
         */
        public Entry {
            Objects.requireNonNull(id, "id==null");
            Objects.requireNonNull(clientId, "clientId==null");
            Objects.requireNonNull(audience, "audience==null");
            realmRoles = realmRoles == null ? List.of() : List.copyOf(realmRoles);
            realmManagementRoles = realmManagementRoles == null ? List.of() : List.copyOf(realmManagementRoles);
        }

        /**
         * Constructor for an application that needs no roles provisioned.
         *
         * @param id Identifier used in commands and in the tenant list.
         * @param displayName Human readable name.
         * @param clientId Keycloak client created in a subscribing tenant's realm.
         * @param audience Value the client's audience mapper emits, which the application validates.
         */
        public Entry(final String id, @Nullable final String displayName, final String clientId,
                     final String audience) {
            this(id, displayName, clientId, audience, List.of(), List.of());
        }

    }

}

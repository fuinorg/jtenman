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
package org.fuin.jtenman.shared;

/**
 * The Keycloak <b>realm</b> roles jtenman knows about.
 * <p>
 * Only realm roles appear here, and that is not a simplification: the Keycloak starter's
 * {@code KeycloakJwtAuthenticationConverter} maps {@code realm_access.roles} and deliberately ignores
 * {@code resource_access.*.roles}, so a client role granted to the same person is invisible and the
 * check fails <b>silently</b> - a 403 with nothing wrong in Keycloak's UI to point at.
 * <p>
 * A role is never assigned to a person or to a service account directly. It is carried by a group and
 * the account is placed in that group - see {@code steering/security.md}. The names below are the
 * contract between that Keycloak setup and the filter chain in
 * {@code ControlPlaneSecurityAutoConfiguration}; they are written without the {@code ROLE_} prefix
 * Spring Security adds, which is what {@code hasRole(..)} expects.
 */
public final class JtenmanRoles {

    /**
     * Administers the control plane: registering tenants, inviting their administrators, subscribing
     * them to applications, suspending, resuming and deleting them. Every {@code /cmd/**} call needs it.
     * <p>
     * It is the most privileged role in the system - it creates realms - so nothing else may be folded
     * into it.
     */
    public static final String TENANT_ADMIN = "tenant-admin";

    /**
     * Machine role for the registry pull: an administered application's scheduler polling the tenant
     * list. Read only, and it is transport authority rather than domain authority - it says "this caller
     * may fetch the list", nothing about who may change it.
     * <p>
     * No human ever holds it. A shared role would make "person or scheduler?" unanswerable in both
     * authorization and audit.
     */
    public static final String SVC_TENANT_READ = "svc-tenant-read";

    private JtenmanRoles() {
        throw new UnsupportedOperationException("It's not allowed to create an instance of this utility class");
    }

}

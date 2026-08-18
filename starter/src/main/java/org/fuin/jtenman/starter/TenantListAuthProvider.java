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

import org.fuin.objects4j.common.ThreadSafe;

import java.util.Optional;

/**
 * Supplies the bearer token the tenant list is fetched with.
 * <p>
 * jtenman requires a role on that endpoint, so a real deployment needs a token here. It is a seam rather
 * than a property because the token is short lived and has to be obtained, not configured: the intended
 * implementation asks Keycloak for a client-credentials token of the {@code svc-tenant-read} service
 * account and caches it until it expires. Reading a static token from configuration would be a
 * long-lived credential in a file, which is what that rule exists to avoid.
 * <p>
 * Called once per pull, on the refresh thread, never on a request thread.
 * <p>
 * The default is {@link NoOpTenantListAuthProvider}, which sends no token at all. That is only useful
 * against a jtenman reachable on a trusted network with its authorization relaxed; anywhere else the
 * pull answers 401 and the list stays empty - which fails closed, and says so in the log.
 */
@ThreadSafe
public interface TenantListAuthProvider {

    /**
     * Returns the token to send, if there is one.
     *
     * @return Bearer token without the {@code Bearer } prefix, or empty to send no
     *         {@code Authorization} header at all.
     */
    Optional<String> bearerToken();

}

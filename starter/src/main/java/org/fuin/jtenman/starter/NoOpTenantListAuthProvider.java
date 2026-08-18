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
 * Default {@link TenantListAuthProvider} that sends no token.
 * <p>
 * Present so the starter is usable before a service account exists, not because an unauthenticated pull
 * is acceptable. jtenman answers 401, the list stays empty and the application accepts nobody - the
 * failure is loud and closed rather than quiet and open.
 */
@ThreadSafe
public class NoOpTenantListAuthProvider implements TenantListAuthProvider {

    @Override
    public Optional<String> bearerToken() {
        // Intentionally no token - see the class comment.
        return Optional.empty();
    }

}

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

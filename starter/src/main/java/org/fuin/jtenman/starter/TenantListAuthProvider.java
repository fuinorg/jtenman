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

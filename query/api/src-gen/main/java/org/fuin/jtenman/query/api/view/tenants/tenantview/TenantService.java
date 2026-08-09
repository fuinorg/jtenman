package org.fuin.jtenman.query.api.view.tenants.tenantview;

import java.util.List;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;

/**
 * The "Tenant" read model as plain Java: the operations that MUST be provided, with no
 * implementation, no persistence assumptions and no framework types. Regenerated on every
 * build.
 *
 * <p>This is the contract to depend on from inside the same application. The REST contracts
 * {@link TenantControllerApi} and {@link TenantResourceApi} declare the same
 * operations for a caller in another process; the generated REST classes implementing them
 * do nothing but delegate here, so going through HTTP inside one JVM would buy nothing.
 *
 * <p>An operation the model declares {@code optional} returns an {@link java.util.Optional}.
 * Over HTTP that same absence is a 404, which the generated delegates translate in both
 * directions.
 */
public interface TenantService {
    
    /**
     * Returns the active tenants subscribed to one application. <p> Suspended tenants are left out rather than returned with their state: a caller that forgets to filter would otherwise keep accepting a suspended tenant's tokens.
     *
     * @param application The application to list the tenants of.
     *
     * @return The active tenants subscribed to that application.
     */
    public List<TenantDetails> listByApplication(final ApplicationId application);
    
}

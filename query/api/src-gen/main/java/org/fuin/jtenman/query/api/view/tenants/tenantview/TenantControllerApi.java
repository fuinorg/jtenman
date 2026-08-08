package org.fuin.jtenman.query.api.view.tenants.tenantview;

import java.util.List;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * REST contract for the "Tenant" view: an {@code @HttpExchange} interface usable by an
 * HTTP-interface client and implemented by the TenantController server class (which adds
 * {@code @RestController}). Declares the operations that MUST be provided - no implementation
 * and no persistence assumptions. Regenerated on every build.
 *
 * <p>Spring flavour - requires {@code org.springframework:spring-web}, which the module owning
 * this interface declares as an <em>optional</em> dependency. Add that dependency to whatever
 * uses this interface. The Quarkus flavour {@link TenantResourceApi} is generated alongside
 * it and declares the same operations; use one or the other, not both.
 */
@HttpExchange("/view/tenant")
public interface TenantControllerApi {

    /**
     * Returns the active tenants subscribed to one application. <p> Suspended tenants are left out rather than returned with their state: a caller that forgets to filter would otherwise keep accepting a suspended tenant's tokens.
     *
     * @param application The application to list the tenants of.
     *
     * @return The active tenants subscribed to that application.
     */
    @GetExchange("/list-by-application")
    ResponseEntity<List<TenantDetails>> listByApplication(@RequestParam("application") final ApplicationId application);

}

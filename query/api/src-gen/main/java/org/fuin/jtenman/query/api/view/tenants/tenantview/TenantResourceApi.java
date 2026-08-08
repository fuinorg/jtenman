package org.fuin.jtenman.query.api.view.tenants.tenantview;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;

/**
 * REST contract for the "Tenant" view: a MicroProfile REST Client interface, also
 * implemented by the TenantResource server class. Declares the operations that MUST be
 * provided - no implementation and no persistence assumptions. Regenerated on every build.
 *
 * <p>As a client, inject it with {@code @RestClient} and point it at a server with
 * {@code quarkus.rest-client.tenant.url=...}. JAX-RS does not inherit class-level
 * annotations, so the server class re-declares {@code @Path}; the method annotations below are
 * inherited by the implementation.
 *
 * <p>Quarkus flavour - requires {@code jakarta.ws.rs:jakarta.ws.rs-api} and
 * {@code org.eclipse.microprofile.rest.client:microprofile-rest-client-api}, which the module
 * owning this interface declares as <em>optional</em> dependencies (a Quarkus consumer gets
 * both from {@code quarkus-rest-client}). Add them to whatever uses this interface. The Spring
 * flavour {@link TenantControllerApi} is generated alongside it and declares the same
 * operations; use one or the other, not both.
 */
@Path("/view/tenant")
@RegisterRestClient(configKey = "tenant")
public interface TenantResourceApi {

    /**
     * Returns the active tenants subscribed to one application. <p> Suspended tenants are left out rather than returned with their state: a caller that forgets to filter would otherwise keep accepting a suspended tenant's tokens.
     *
     * @param application The application to list the tenants of.
     *
     * @return The active tenants subscribed to that application.
     */
    @GET
    @Path("/list-by-application")
    @Produces(MediaType.APPLICATION_JSON)
    List<TenantDetails> listByApplication(@QueryParam("application") final ApplicationId application);

}

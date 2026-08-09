package org.fuin.jtenman.query.api.view.tenants.tenantview;

import java.util.List;
import java.util.Objects;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.springframework.http.ResponseEntity;

/**
 * Satisfies {@link TenantService} over HTTP, by wrapping the generated
 * {@code @HttpExchange} proxy for {@link TenantControllerApi} and unwrapping its
 * {@link ResponseEntity}. Regenerated on every build.
 *
 * <p>A 404 is an answer here, not a failure: for an operation the model declares
 * {@code optional} it becomes an empty result, which is the same thing the service reports
 * when it runs in this JVM. Both a thrown {@code HttpClientErrorException.NotFound} and a
 * returned 404 status are handled, because which of the two a caller sees depends on how
 * the underlying client was configured.
 *
 * <p>Carries no bean-defining annotation on purpose: it is wired explicitly by whatever
 * application needs it, and must stay inert on a classpath that is scanned for beans.
 */
public class TenantServiceRestClient implements TenantService {

    private final TenantControllerApi api;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param api Proxy for the read model of another process.
     */
    public TenantServiceRestClient(final TenantControllerApi api) {
        this.api = Objects.requireNonNull(api, "api==null");
    }

    @Override
    public List<TenantDetails> listByApplication(final ApplicationId application) {
        final ResponseEntity<List<TenantDetails>> response = api.listByApplication(application);
        final List<TenantDetails> body = response.getBody();
        if (body == null) {
            throw new IllegalStateException(
                    "The query side answered 'listByApplication' with an empty body");
        }
        return body;
    }

}

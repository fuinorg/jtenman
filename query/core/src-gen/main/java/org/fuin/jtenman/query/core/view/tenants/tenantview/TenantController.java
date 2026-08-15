package org.fuin.jtenman.query.core.view.tenants.tenantview;

import java.util.List;
import java.util.Objects;
import org.fuin.cqrs4j.core.QueryAuthorization;
import org.fuin.cqrs4j.core.QueryAuthorizer;
import org.fuin.cqrs4j.core.QueryExecutionContextProvider;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantControllerApi;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantService;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the Tenant read model over REST by forwarding to {@link TenantService}.
 * Implements {@link TenantControllerApi} and adds {@code @RestController} (required - not inherited
 * from the interface). Holds no logic of its own and is regenerated on every build -
 * implement the queries in TenantServiceImpl.
 */
@RestController
public class TenantController implements TenantControllerApi {

    private final TenantService service;

    private final QueryAuthorizer authorizer;

    private final QueryExecutionContextProvider contextProvider;

    /**
     * Constructor with all mandatory dependencies. A single constructor is autowired
     * implicitly.
     *
     * @param service Read model this controller exposes.
     * @param authorizer Decides whether the caller may invoke an operation.
     * @param contextProvider Says who is calling.
     */
    public TenantController(final TenantService service, final QueryAuthorizer authorizer,
            final QueryExecutionContextProvider contextProvider) {
        this.service = Objects.requireNonNull(service, "service==null");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer==null");
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider==null");
    }

    @Override
    public ResponseEntity<List<TenantDetails>> listByApplication(@RequestParam("application") final ApplicationId application) {
        QueryAuthorization.require(authorizer, "TenantView.listByApplication", contextProvider.current());
        return ResponseEntity.ok(service.listByApplication(application));
    }

}

package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantControllerApi;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.IssuerUri;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.dsl.cqrs.common.basics.VersionedEntityIdPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller providing the Tenant read model. Implements {@link TenantControllerApi} and adds
 * {@code @RestController} (required - not inherited from the interface).
 * <p>
 * This is the contract consuming applications poll to keep their local tenant replica current, so it is
 * a machine interface: its shape and its path are part of jtenman's published API.
 */
@RestController
@Transactional(readOnly = true)
public class TenantController implements TenantControllerApi {

    @Autowired
    private EntityManager em;

    @Override
    public ResponseEntity<List<TenantDetails>> listByApplication(
            @RequestParam("application") final ApplicationId application) {

        // Only ACTIVE tenants are returned. Handing out a suspended one and trusting every caller to
        // filter would make one forgotten check anywhere in the system into live access for a tenant
        // whose access was revoked.
        final List<Object[]> rows = em.createQuery(
                        "SELECT t.realm, t.issuerUri, t.status, t.entityIdPath, t.aggregateVersion "
                                + "FROM TenantEntity t, TenantApplicationEntity a "
                                + "WHERE a.realm = t.realm AND a.application = :application "
                                + "AND t.status = :status ORDER BY t.realm", Object[].class)
                .setParameter("application", application.asBaseType())
                .setParameter("status", TenantStatus.ACTIVE.name())
                .getResultList();

        return ResponseEntity.ok(rows.stream().map(TenantController::toDetails).toList());
    }

    private static TenantDetails toDetails(final Object[] row) {
        final String realm = (String) row[0];
        // The path is rebuilt from the realm rather than parsed back out of the stored string: the realm
        // is the aggregate identifier, so this cannot disagree with it.
        return new TenantDetails(
                new VersionedEntityIdPath(new EntityIdPath(new TenantRealmId(realm)), (Integer) row[4]),
                new RealmName(realm),
                new IssuerUri((String) row[1]),
                TenantStatus.valueOf((String) row[2]));
    }

}

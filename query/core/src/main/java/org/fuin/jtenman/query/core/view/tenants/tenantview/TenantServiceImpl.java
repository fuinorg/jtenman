package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.EntityManager;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantService;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.IssuerUri;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.dsl.cqrs.common.basics.VersionedEntityIdPath;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Answers the Tenant read model's queries against the database. Implements {@link TenantService},
 * which the generated TenantController exposes over REST and a caller in this JVM uses directly.
 * <p>
 * What consuming applications poll to keep their local tenant replica current reaches them through that
 * controller, so the REST shape and path stay part of jtenman's published API - they are just no longer
 * declared here.
 */
@Transactional(readOnly = true)
public class TenantServiceImpl implements TenantService {

    private final EntityManager em;

    /**
     * Constructor with all mandatory dependencies. A single constructor is autowired implicitly, and it
     * is what lets a test drive this service against an in-memory database without starting Spring.
     *
     * @param em Entity manager of the read model.
     */
    public TenantServiceImpl(final EntityManager em) {
        this.em = Objects.requireNonNull(em, "em==null");
    }

    @Override
    public List<TenantDetails> listByApplication(final ApplicationId application) {

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

        return rows.stream().map(TenantServiceImpl::toDetails).toList();
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

package org.fuin.jtenman.query.core.view.tenants.tenantview;

import org.fuin.jtenman.query.core.view.ReadModelTest;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.IssuerUri;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the tenant read model's query.
 * <p>
 * The case that matters most is the negative one: a suspended tenant must not be returned. This is the
 * list consuming applications poll to decide whose tokens to accept, so a suspended tenant leaking into
 * it would mean live access for someone whose access was revoked - and it would leak silently, because
 * every positive assertion would still pass.
 */
class TenantReadModelTest extends ReadModelTest {

    private static final ApplicationId BILLING = new ApplicationId("billing");

    private static final ApplicationId REPORTING = new ApplicationId("reporting");

    @Test
    void testAnActiveSubscribedTenantIsReturned() {

        // PREPARE
        givenTenant("acme", "https://id.acme.test/realms/acme", TenantStatus.ACTIVE, 3);
        givenSubscription("acme", BILLING);

        // TEST
        final List<TenantDetails> result = service().listByApplication(BILLING);

        // VERIFY - the whole row is mapped, including the version the replica uses to detect a change.
        assertThat(result).hasSize(1);
        final TenantDetails details = result.getFirst();
        assertThat(details.getRealm()).isEqualTo(new RealmName("acme"));
        assertThat(details.getIssuerUri()).isEqualTo(new IssuerUri("https://id.acme.test/realms/acme"));
        assertThat(details.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(details.getSource().getAggregateVersion()).isEqualTo(3);
        assertThat(details.getSource().getEntityIdPath().first().asString()).isEqualTo("acme");
    }

    @Test
    void testASuspendedTenantIsLeftOutRatherThanReturnedWithItsState() {

        // PREPARE - subscribed to the application, but no longer allowed to use it.
        givenTenant("acme", "https://id.acme.test/realms/acme", TenantStatus.SUSPENDED, 7);
        givenSubscription("acme", BILLING);

        // TEST + VERIFY - not "returned as SUSPENDED for the caller to filter": absent. One forgotten
        // check in one consuming application would otherwise be a live tenant.
        assertThat(service().listByApplication(BILLING)).isEmpty();
    }

    @Test
    void testATenantSubscribedToAnotherApplicationIsNotReturned() {

        // PREPARE
        givenTenant("acme", "https://id.acme.test/realms/acme", TenantStatus.ACTIVE, 1);
        givenSubscription("acme", REPORTING);

        // TEST + VERIFY
        assertThat(service().listByApplication(BILLING)).isEmpty();
    }

    @Test
    void testOnlyTheSubscribedTenantsAreReturnedOrderedByRealm() {

        // PREPARE - three active tenants, two of them on the application asked about, inserted in an
        // order that is not the expected one.
        givenTenant("zeta", "https://id.zeta.test/realms/zeta", TenantStatus.ACTIVE, 1);
        givenTenant("acme", "https://id.acme.test/realms/acme", TenantStatus.ACTIVE, 1);
        givenTenant("other", "https://id.other.test/realms/other", TenantStatus.ACTIVE, 1);
        givenSubscription("zeta", BILLING);
        givenSubscription("acme", BILLING);
        givenSubscription("other", REPORTING);

        // TEST
        final List<TenantDetails> result = service().listByApplication(BILLING);

        // VERIFY - ordered in the database, so a replica sees a stable sequence across polls.
        assertThat(result).extracting(details -> details.getRealm().asBaseType())
                .containsExactly("acme", "zeta");
    }

    @Test
    void testAnApplicationNobodySubscribesToIsEmptyRatherThanAFailure() {

        // PREPARE
        givenTenant("acme", "https://id.acme.test/realms/acme", TenantStatus.ACTIVE, 1);
        givenSubscription("acme", BILLING);

        // TEST + VERIFY
        assertThat(service().listByApplication(REPORTING)).isEmpty();
    }

    @Test
    void testATenantWithoutAnySubscriptionIsNotReturned() {

        // PREPARE - the tenant exists and is active, but subscribes to nothing.
        givenTenant("acme", "https://id.acme.test/realms/acme", TenantStatus.ACTIVE, 1);

        // TEST + VERIFY
        assertThat(service().listByApplication(BILLING)).isEmpty();
    }

    private TenantServiceImpl service() {
        return new TenantServiceImpl(em);
    }

    private void givenTenant(final String realm, final String issuerUri, final TenantStatus status,
            final int version) {
        inTransaction(em -> {
            final TenantEntity row = new TenantEntity();
            row.setRealm(realm);
            row.setIssuerUri(issuerUri);
            row.setStatus(status.name());
            row.setEntityIdPath(realm);
            row.setAggregateVersion(version);
            em.persist(row);
        });
    }

    private void givenSubscription(final String realm, final ApplicationId application) {
        inTransaction(em -> {
            final TenantApplicationEntity row = new TenantApplicationEntity();
            // The key the projection builds, so a query by application is an indexed lookup.
            row.setId(realm + ":" + application.asBaseType());
            row.setRealm(realm);
            row.setApplication(application.asBaseType());
            em.persist(row);
        });
    }

}

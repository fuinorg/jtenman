package org.fuin.jtenman.starter;

import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenant;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.fuin.dsl.cqrs.common.basics.VersionedEntityIdPath;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantService;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.IssuerUri;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link JtenmanTenantRepository}.
 * <p>
 * Nothing here talks to jtenman or to Keycloak: the fetch and the OpenID Connect discovery are the two
 * overridable seams of the class under test, so what is exercised is the part that decides who is
 * accepted - the admission list, the events that drive cache eviction downstream, and the two ways this
 * repository refuses everybody.
 */
class JtenmanTenantRepositoryTest {

    private static final String BASE = "http://localhost:8180/realms/";

    private static final ApplicationId BILLING = new ApplicationId("billing");

    @Test
    void testItAcceptsOnlyTheRealmsJtenmanNamed() {

        final Testee testee = testee(tenants("acme", "globex"));
        testee.refresh();

        assertThat(testee.findByIssuer(BASE + "acme")).isPresent();
        assertThat(testee.findByIssuer(BASE + "globex")).isPresent();
        // The realm-discovering repository would go and look this one up. This one does not.
        assertThat(testee.findByIssuer(BASE + "intruder")).isEmpty();
        assertThat(testee.resolved).containsExactlyInAnyOrder(BASE + "acme", BASE + "globex");

        assertThat(testee.getTenantIds()).containsExactlyInAnyOrder(new TenantId("acme"), new TenantId("globex"));
    }

    /**
     * The reason this repository exists at all. A realm that leaves the list has to be announced, because
     * the issuer validator and the key selector cache what they resolved and only drop it on the event -
     * without it an already-issued token keeps working for its full lifetime.
     */
    @Test
    void testATenantThatLeavesTheListIsAnnouncedAndRejected() {

        final Testee testee = testee(tenants("acme", "globex"));
        testee.refresh();
        testee.events.clear();

        testee.answer.set(tenants("acme"));
        testee.refresh();

        assertThat(testee.findByIssuer(BASE + "globex")).isEmpty();
        assertThat(testee.removed()).containsExactly(new TenantId("globex"));
        assertThat(testee.added()).isEmpty();
    }

    @Test
    void testATenantThatJoinsTheListIsAnnouncedAndAccepted() {

        final Testee testee = testee(tenants("acme"));
        testee.refresh();
        testee.events.clear();

        testee.answer.set(tenants("acme", "globex"));
        testee.refresh();

        assertThat(testee.findByIssuer(BASE + "globex")).isPresent();
        assertThat(testee.added()).containsExactly(new TenantId("globex"));
        assertThat(testee.removed()).isEmpty();
    }

    /**
     * A refresh that changes nothing must stay silent. The listeners rebuild a datasource on an add, so a
     * repeated announcement of an unchanged list is not merely noise.
     */
    @Test
    void testAnUnchangedListAnnouncesNothingAndResolvesNothingTwice() {

        final Testee testee = testee(tenants("acme"));
        testee.refresh();
        testee.events.clear();
        testee.resolved.clear();

        testee.refresh();

        assertThat(testee.events).isEmpty();
        assertThat(testee.resolved).isEmpty();
    }

    /**
     * Before the first successful pull the application has not been told which realms are its tenants.
     * Accepting any of them on that basis is the hole the control plane exists to close.
     */
    @Test
    void testItAcceptsNobodyBeforeTheFirstSuccessfulPull() {

        final Testee testee = testee(tenants("acme"));

        assertThat(testee.usable()).isFalse();
        assertThat(testee.findByIssuer(BASE + "acme")).isEmpty();
        assertThat(testee.getTenantIds()).isEmpty();
        assertThat(testee.lastSuccessfulRefresh()).isEmpty();
    }

    /**
     * A failed pull says nothing about who the tenants are, so the previous list stays - but only up to
     * the configured bound, after which a revocation list nobody can verify stops being trusted.
     */
    @Test
    void testAListThatCannotBeRefreshedIsTrustedUntilItIsTooOld() {

        final Testee testee = testee(tenants("acme"), Duration.ofMinutes(5));
        testee.refresh();

        testee.answer.set(null); // jtenman is unreachable from here on
        assertThatThrownBy(testee::refresh).isInstanceOf(IllegalStateException.class);

        testee.clock += Duration.ofMinutes(4).toMillis();
        assertThat(testee.usable()).isTrue();
        assertThat(testee.findByIssuer(BASE + "acme")).isPresent();

        testee.clock += Duration.ofMinutes(2).toMillis();
        assertThat(testee.usable()).isFalse();
        assertThat(testee.findByIssuer(BASE + "acme")).isEmpty();
        assertThat(testee.getTenantIds()).isEmpty();
    }

    /**
     * The operator may trade the guarantee above for availability. It has to be an explicit zero, not an
     * omission.
     */
    @Test
    void testTheStalenessBoundCanBeSwitchedOff() {

        final Testee testee = testee(tenants("acme"), Duration.ZERO);
        testee.refresh();

        testee.clock += Duration.ofDays(30).toMillis();

        assertThat(testee.usable()).isTrue();
        assertThat(testee.findByIssuer(BASE + "acme")).isPresent();
    }

    /**
     * jtenman leaves suspended tenants out of the answer already. This is the second filter, so a change
     * on that side cannot silently widen what an application accepts.
     */
    @Test
    void testASuspendedTenantIsNotAccepted() {

        final Testee testee = testee(List.of(
                details("acme", TenantStatus.ACTIVE),
                details("globex", TenantStatus.SUSPENDED)));
        testee.refresh();

        assertThat(testee.findByIssuer(BASE + "globex")).isEmpty();
        assertThat(testee.getTenantIds()).containsExactly(new TenantId("acme"));
    }

    /**
     * Discovery failing for one realm must not cost the others their list entry, and the realm must be
     * retried rather than dropped - it is still a tenant, its keys are just unknown for now.
     */
    @Test
    void testATenantWhoseKeysCannotBeResolvedIsRejectedButKept() {

        final Testee testee = testee(tenants("acme", "globex"));
        testee.unresolvable.add(BASE + "globex");
        testee.refresh();

        assertThat(testee.findByIssuer(BASE + "acme")).isPresent();
        assertThat(testee.findByIssuer(BASE + "globex")).isEmpty();
        // Still admitted, so it is still announced and still refreshed.
        assertThat(testee.getTenantIds()).containsExactlyInAnyOrder(new TenantId("acme"), new TenantId("globex"));
        assertThat(testee.added()).containsExactlyInAnyOrder(new TenantId("acme"), new TenantId("globex"));

        testee.unresolvable.clear();
        testee.refresh();

        assertThat(testee.findByIssuer(BASE + "globex")).isPresent();
    }

    /**
     * A resource server reads the tenant off the issuer's last segment, so a realm named anything else is
     * one tenant under two identifiers. The half that would be silent is the event store: streams written
     * under the issuer's segment, projected under jtenman's realm name, and a read model that stays empty
     * while everything reports fine.
     */
    @Test
    void testARealmThatIsNotItsIssuersLastSegmentIsNotAdmitted() {

        final Testee testee = testee(List.of(
                details("acme", BASE + "acme", TenantStatus.ACTIVE),
                details("globex", BASE + "globex-eu", TenantStatus.ACTIVE)));
        testee.refresh();

        assertThat(testee.getTenantIds()).containsExactly(new TenantId("acme"));
        assertThat(testee.findByIssuer(BASE + "globex-eu")).isEmpty();
        // Not merely unadmitted - never even asked about, so a bad row costs no discovery call either.
        assertThat(testee.resolved).containsExactly(BASE + "acme");
        assertThat(testee.added()).containsExactly(new TenantId("acme"));
    }

    /**
     * The same realm name on two Keycloaks is two organisations and one {@link TenantId}. Both are
     * dropped: nothing here can say which was meant, and admitting either hands one of them the other's
     * streams, schema and checkpoints.
     */
    @Test
    void testARealmClaimedByTwoIssuersIsNotAdmittedAtAll() {

        final Testee testee = testee(List.of(
                details("acme", "http://kc-eu:8180/realms/acme", TenantStatus.ACTIVE),
                details("acme", "http://kc-us:8180/realms/acme", TenantStatus.ACTIVE),
                details("globex", BASE + "globex", TenantStatus.ACTIVE)));
        testee.refresh();

        assertThat(testee.getTenantIds()).containsExactly(new TenantId("globex"));
        assertThat(testee.findByIssuer("http://kc-eu:8180/realms/acme")).isEmpty();
        assertThat(testee.findByIssuer("http://kc-us:8180/realms/acme")).isEmpty();
        assertThat(testee.added()).containsExactly(new TenantId("globex"));
    }

    /**
     * The collision can appear after the fact, and then it is an eviction: the tenant that was being
     * served has to stop being served, and the listeners only drop their caches on the event.
     */
    @Test
    void testATenantThatBecomesAmbiguousIsWithdrawn() {

        final Testee testee = testee(List.of(details("acme", BASE + "acme", TenantStatus.ACTIVE)));
        testee.refresh();
        testee.events.clear();

        testee.answer.set(List.of(
                details("acme", BASE + "acme", TenantStatus.ACTIVE),
                details("acme", "http://kc-us:8180/realms/acme", TenantStatus.ACTIVE)));
        testee.refresh();

        assertThat(testee.getTenantIds()).isEmpty();
        assertThat(testee.findByIssuer(BASE + "acme")).isEmpty();
        assertThat(testee.removed()).containsExactly(new TenantId("acme"));
    }

    /**
     * A bad row must not take the answerable tenants down with it, which is why these are dropped
     * entries rather than a failed pull - a failed pull means "could not ask jtenman" and eventually
     * stops the application serving anybody.
     */
    @Test
    void testAnInadmissibleEntryDoesNotFailTheRefresh() {

        final Testee testee = testee(List.of(
                details("acme", BASE + "acme", TenantStatus.ACTIVE),
                details("globex", BASE + "globex-eu", TenantStatus.ACTIVE)));

        testee.refresh();

        assertThat(testee.usable()).isTrue();
        assertThat(testee.lastSuccessfulRefresh()).isPresent();
        assertThat(testee.findByIssuer(BASE + "acme")).isPresent();
    }

    private static Testee testee(final List<TenantDetails> answer) {
        return testee(answer, Duration.ofMinutes(5));
    }

    private static Testee testee(final List<TenantDetails> answer, final Duration maxStaleness) {
        return new Testee(new AtomicReference<>(answer), maxStaleness, new ArrayList<>());
    }

    private static List<TenantDetails> tenants(final String... realms) {
        final List<TenantDetails> result = new ArrayList<>();
        for (final String realm : realms) {
            result.add(details(realm, TenantStatus.ACTIVE));
        }
        return result;
    }

    private static TenantDetails details(final String realm, final TenantStatus status) {
        return details(realm, BASE + realm, status);
    }

    private static TenantDetails details(final String realm, final String issuerUri,
            final TenantStatus status) {
        return new TenantDetails(
                new VersionedEntityIdPath(new EntityIdPath(new TenantRealmId(realm)), 1),
                new RealmName(realm), new IssuerUri(issuerUri), status);
    }

    /**
     * The class under test with its two network seams replaced and its clock under control.
     */
    private static final class Testee extends JtenmanTenantRepository {

        private final AtomicReference<List<TenantDetails>> answer;

        private final List<Object> events;

        private final Set<String> resolved = new HashSet<>();

        private final Set<String> unresolvable = new HashSet<>();

        private long clock = 1_000_000L;

        private Testee(final AtomicReference<List<TenantDetails>> answer, final Duration maxStaleness,
                final List<Object> events) {
            super(unusedService(), BILLING, maxStaleness, events::add);
            this.answer = answer;
            this.events = events;
        }

        @Override
        protected List<TenantDetails> fetch() {
            final List<TenantDetails> current = answer.get();
            if (current == null) {
                throw new IllegalStateException("jtenman is unreachable");
            }
            return current;
        }

        @Override
        protected JwtTenant resolve(final String issuerUri) {
            if (unresolvable.contains(issuerUri)) {
                throw new IllegalStateException("Keycloak is unreachable for " + issuerUri);
            }
            resolved.add(issuerUri);
            // JwtTenant's only public constructor performs OpenID Connect discovery, and its
            // settings-taking one is package private. Nothing here reads the tenant back, so a mock is
            // enough - what is under test is which issuer gets one at all.
            return Mockito.mock(JwtTenant.class);
        }

        @Override
        protected long now() {
            return clock;
        }

        private List<TenantId> added() {
            return events.stream().filter(TenantAddedEvent.class::isInstance)
                    .map(event -> ((TenantAddedEvent) event).tenant().getTenantId()).toList();
        }

        private List<TenantId> removed() {
            return events.stream().filter(TenantRemovedEvent.class::isInstance)
                    .map(event -> ((TenantRemovedEvent) event).tenant().getTenantId()).toList();
        }

        /**
         * The service is never called - {@link #fetch()} is overridden - but the constructor of the
         * class under test rightly refuses a null one.
         */
        private static TenantService unusedService() {
            return application -> {
                throw new UnsupportedOperationException("fetch() is overridden");
            };
        }

    }

}

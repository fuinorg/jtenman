package org.fuin.jtenman.command.core.domain.tenants;

import org.fuin.dsl.cqrs.common.basics.EmailAddress;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.InviteAdministratorService;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.RegisterTenantService;
import org.fuin.jtenman.command.core.domain.tenants.AbstractTenant.SubscribeApplicationService;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.IssuerUri;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.SubjectId;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@code Tenant.inviteAdministrator} hands its subscriptions to the operation-context service.
 * <p>
 * Which roles the administrators group has to carry depends on what the tenant is subscribed to, and this
 * aggregate is the only place that knows. If it stopped passing them, the group would end up empty and the
 * invited administrator would hold nothing - a failure that looks like a permissions problem in the
 * consuming application rather than like a missing hand-off here.
 */
public class TenantInviteAdministratorTest {

    private static final RealmName REALM = new RealmName("acme");

    private static final EmailAddress EMAIL = new EmailAddress("admin@acme.com");

    @Test
    public void testPassesNoSubscriptionsWhenNothingIsSubscribedYet() throws Exception {
        // The invite-before-subscribe order. Nothing to map yet; createClient maps the roles later onto
        // the group this call creates.
        final RecordingInviteService service = new RecordingInviteService();

        newTenant().inviteAdministrator(EMAIL, service);

        assertThat(service.seenApplications).isEmpty();
        assertThat(service.seenRealm).isEqualTo(REALM);
        assertThat(service.seenEmail).isEqualTo(EMAIL);
    }

    @Test
    public void testPassesEverythingSubscribedSoFar() throws Exception {
        // The subscribe-before-invite order. The roles of both applications must be re-ensured, because
        // this is the call that creates the group the administrator is placed into.
        final Tenant testee = newTenant();
        testee.subscribeApplication(new ApplicationId("melkheftken"), new StubSubscribeService());
        testee.subscribeApplication(new ApplicationId("other"), new StubSubscribeService());

        final RecordingInviteService service = new RecordingInviteService();
        testee.inviteAdministrator(EMAIL, service);

        assertThat(service.seenApplications)
                .containsExactly(new ApplicationId("melkheftken"), new ApplicationId("other"));
    }

    private static Tenant newTenant() throws Exception {
        return new Tenant(new TenantRealmId(REALM.asBaseType()), REALM, new StubRegisterService());
    }

    private static final class RecordingInviteService implements InviteAdministratorService {

        private RealmName seenRealm;

        private EmailAddress seenEmail;

        private final List<ApplicationId> seenApplications = new ArrayList<>();

        @Override
        public SubjectId inviteRealmAdministrator(final RealmName realm, final EmailAddress email,
                final List<ApplicationId> subscribedApplications) {
            this.seenRealm = realm;
            this.seenEmail = email;
            this.seenApplications.addAll(subscribedApplications);
            return new SubjectId("subject-1");
        }

    }

    private static final class StubRegisterService implements RegisterTenantService {

        @Override
        public boolean realmExists(final RealmName realm) {
            return false;
        }

        @Override
        public IssuerUri createRealm(final RealmName realm) {
            return new IssuerUri("http://localhost:8180/realms/" + realm.asBaseType());
        }

    }

    private static final class StubSubscribeService implements SubscribeApplicationService {

        @Override
        public boolean isKnownApplication(final ApplicationId application) {
            return true;
        }

        @Override
        public void createClient(final RealmName realm, final ApplicationId application) {
            // Nothing - this test is about what the aggregate hands over, not about Keycloak.
        }

    }

}

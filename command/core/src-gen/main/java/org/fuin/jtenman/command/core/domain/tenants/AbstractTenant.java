package org.fuin.jtenman.command.core.domain.tenants;

import org.fuin.ddd4j.core.AbstractAggregateRoot;
import org.fuin.ddd4j.core.EntityType;
import org.fuin.dsl.cqrs.common.basics.EmailAddress;
import org.fuin.jtenman.shared.domain.tenants.AdministratorInvitedEvent;
import org.fuin.jtenman.shared.domain.tenants.ApplicationAlreadySubscribedException;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.ApplicationNotSubscribedException;
import org.fuin.jtenman.shared.domain.tenants.ApplicationSubscribedEvent;
import org.fuin.jtenman.shared.domain.tenants.ApplicationUnsubscribedEvent;
import org.fuin.jtenman.shared.domain.tenants.IssuerUri;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.SubjectId;
import org.fuin.jtenman.shared.domain.tenants.SuspensionReason;
import org.fuin.jtenman.shared.domain.tenants.TenantAlreadyDeletedException;
import org.fuin.jtenman.shared.domain.tenants.TenantDeletedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantNotSuspendedException;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.jtenman.shared.domain.tenants.TenantRegisteredEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantResumedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantSuspendedEvent;
import org.fuin.jtenman.shared.domain.tenants.UnknownApplicationException;
import org.fuin.objects4j.common.Contract;

/**
 * A customer of the system, represented by one Keycloak realm, together with the applications it is allowed to use. <p> The subscriptions live here rather than in an aggregate of their own: a subscription has no lifecycle independent of the tenant, and suspending must revoke access to every application at once, which is a single-aggregate invariant. The collection is bounded by the number of applications in the system.
 */
public abstract class AbstractTenant extends AbstractAggregateRoot<TenantRealmId> {

    @SuppressWarnings("NullAway.Init")
    private TenantRealmId id;

    /**
     * Default constructor for loading the aggregate from its history. The identity comes
     * from the event that created it (see setId below).
     */
    @SuppressWarnings("NullAway.Init")
    protected AbstractTenant() {
        super();
    }

    /**
     * Constructor with the identity, used when the aggregate is created. Having it up front
     * means every operation of the final class can rely on getId(), including the
     * constructor that is still applying the event which brings the aggregate into being.
     *
     * @param id Unique aggregate identifier.
     */
    protected AbstractTenant(final TenantRealmId id) {
        super();
        // Checked here because a "super(id)" has to be the first statement of the creating
        // constructor, leaving it no place to check the identity it passes on.
        Contract.requireArgNotNull("id", id);
        this.id = id;
    }

    @Override
    public final EntityType getType() {
        return TenantRealmId.TYPE;
    }

    @Override
    public final TenantRealmId getId() {
        return id;
    }

    /**
     * Sets the aggregate identifier. Called from the event handler that brings the
     * aggregate into existence, which also runs when it is replayed from past events,
     * so this must never throw.
     *
     * @param id Unique aggregate identifier.
     */
    protected final void setId(final TenantRealmId id) {
        this.id = id;
    }

    /**
     * Handles: TenantRegisteredEvent.
     *
     * @param event Event to handle.
     */
    protected abstract void handleTenantRegisteredEvent(final TenantRegisteredEvent event);
    
    /**
     * Handles: AdministratorInvitedEvent.
     *
     * @param event Event to handle.
     */
    protected abstract void handleAdministratorInvitedEvent(final AdministratorInvitedEvent event);
    
    /**
     * Handles: ApplicationSubscribedEvent.
     *
     * @param event Event to handle.
     */
    protected abstract void handleApplicationSubscribedEvent(final ApplicationSubscribedEvent event);
    
    /**
     * Handles: ApplicationUnsubscribedEvent.
     *
     * @param event Event to handle.
     */
    protected abstract void handleApplicationUnsubscribedEvent(final ApplicationUnsubscribedEvent event);
    
    /**
     * Handles: TenantSuspendedEvent.
     *
     * @param event Event to handle.
     */
    protected abstract void handleTenantSuspendedEvent(final TenantSuspendedEvent event);
    
    /**
     * Handles: TenantDeletedEvent.
     *
     * @param event Event to handle.
     */
    protected abstract void handleTenantDeletedEvent(final TenantDeletedEvent event);
    
    /**
     * Handles: TenantResumedEvent.
     *
     * @param event Event to handle.
     */
    protected abstract void handleTenantResumedEvent(final TenantResumedEvent event);
    
    /**
     * Creates the tenant's realm in Keycloak.
     */
    public interface RegisterTenantService {
        
        /**
         * Returns true if a realm of that name already exists.
         *
         * @param realm The realm to look for.
         *
         * @return True if the realm already exists.
         */
        public boolean realmExists(final RealmName realm);
        
        /**
         * Creates the realm and returns the issuer URI its tokens will carry.
         *
         * @param realm The realm to create.
         *
         * @return Issuer URI of the created realm.
         */
        public IssuerUri createRealm(final RealmName realm);
        
    }
    
    /**
     * Creates the person in the tenant's realm and sends the invitation.
     */
    public interface InviteAdministratorService {
        
        /**
         * Creates a person in the tenant's realm, puts them into the group carrying the tenant-administrator role - roles are never assigned to a user directly - and sends a one-time link asking them to set a password. No credential is set here.
         *
         * @param realm The realm to create the person in.
         * @param email Where to send the invitation.
         *
         * @return Subject id of the created person.
         */
        public SubjectId inviteRealmAdministrator(final RealmName realm, final EmailAddress email);
        
    }
    
    /**
     * Resolves the application in the catalogue and provisions its Keycloak client.
     */
    public interface SubscribeApplicationService {
        
        /**
         * Returns true if the identifier is part of the configured application catalogue.
         *
         * @param application The identifier to check.
         *
         * @return True if the catalogue contains it.
         */
        public boolean isKnownApplication(final ApplicationId application);
        
        /**
         * Creates the application's client in the tenant's realm, including the audience mapper without which the application rejects every token of this tenant.
         *
         * @param realm The realm to create the client in.
         * @param application The application whose client is created.
         */
        public void createClient(final RealmName realm, final ApplicationId application);
        
    }
    
    /**
     * Removes the application's Keycloak client from the tenant's realm.
     */
    public interface UnsubscribeApplicationService {
        
        /**
         * Deletes the application's client in the tenant's realm.
         *
         * @param realm The realm to remove the client from.
         * @param application The application whose client is removed.
         */
        public void removeClient(final RealmName realm, final ApplicationId application);
        
    }
    
    /**
     * Disables the tenant's realm in Keycloak.
     */
    public interface SuspendTenantService {
        
        /**
         * Disables the realm, so no new token can be obtained for it.
         *
         * @param realm The realm to disable.
         */
        public void disableRealm(final RealmName realm);
        
    }
    
    /**
     * Removes the tenant's realm from Keycloak.
     */
    public interface DeleteTenantService {
        
        /**
         * Deletes the realm and everything in it - users, groups, clients.
         *
         * @param realm The realm to delete.
         */
        public void removeRealm(final RealmName realm);
        
    }
    
    /**
     * Enables the tenant's realm in Keycloak again.
     */
    public interface ResumeTenantService {
        
        /**
         * Enables the realm.
         *
         * @param realm The realm to enable.
         */
        public void enableRealm(final RealmName realm);
        
    }
    
    /**
     * Invites a person to administer the tenant's realm, so the tenant can manage its own users from that point on. <p> A freshly registered tenant has a realm nobody can enter. This is what makes it usable, and it is a separate operation from registering on purpose: creating the realm and inviting a person are two calls to Keycloak with no shared transaction, so folding them together would leave a realm that exists but can never be reached if the second one fails. Separate, the invitation can simply be repeated - which is also what "resend the invitation" needs. <p> <b>No password is ever created, transmitted or stored.</b> The person is created with the required actions to set their own credential and receives a one-time link by email. jtenman therefore never holds a working credential for any tenant.
     *
     * @param email Where to send the invitation. Used to send it and then forgotten - only the resulting subject id becomes part of the event stream.
     * @param inviteAdministratorService Creates the person in the tenant's realm and sends the invitation.
     *
     * @throws TenantAlreadyDeletedException An operation was attempted on a tenant that has already been deleted. <p> Deleting removes the aggregate as well as recording the fact, so normally the tenant cannot be reached at all. This covers the window in between: recording the deletion and removing the aggregate are two calls with no shared transaction, so a tenant can survive its own deletion - and it must not act afterwards, least of all against a realm that is already gone.
     */
    public abstract void inviteAdministrator(final EmailAddress email, final InviteAdministratorService inviteAdministratorService) throws TenantAlreadyDeletedException;
    
    /**
     * Grants the tenant access to an application by creating that application's client and its audience mapper in the tenant's realm. <p> Both halves are needed before the tenant can use the application: the consuming application only sees tenants subscribed to it, and it rejects tokens that do not carry its audience.
     *
     * @param application The application to grant access to.
     * @param subscribeApplicationService Resolves the application in the catalogue and provisions its Keycloak client.
     *
     * @throws TenantAlreadyDeletedException An operation was attempted on a tenant that has already been deleted. <p> Deleting removes the aggregate as well as recording the fact, so normally the tenant cannot be reached at all. This covers the window in between: recording the deletion and removing the aggregate are two calls with no shared transaction, so a tenant can survive its own deletion - and it must not act afterwards, least of all against a realm that is already gone.
     * @throws UnknownApplicationException An application that is not part of the configured catalogue was named. Which applications exist is deployment configuration, not domain state, so an unknown identifier is a mistake rather than something to record.
     * @throws ApplicationAlreadySubscribedException The tenant is already subscribed to that application. Subscribing twice would create a second Keycloak client for the same purpose.
     */
    public abstract void subscribeApplication(final ApplicationId application, final SubscribeApplicationService subscribeApplicationService) throws TenantAlreadyDeletedException, UnknownApplicationException, ApplicationAlreadySubscribedException;
    
    /**
     * Withdraws the tenant's access to an application and removes that application's client from its realm again. <p> Removing the client matters: dropping the tenant from the application's list alone leaves a realm able to mint tokens carrying that application's audience, which becomes live access again as soon as anything trusts the audience by itself.
     *
     * @param application The application to withdraw access to.
     * @param unsubscribeApplicationService Removes the application's Keycloak client from the tenant's realm.
     *
     * @throws TenantAlreadyDeletedException An operation was attempted on a tenant that has already been deleted. <p> Deleting removes the aggregate as well as recording the fact, so normally the tenant cannot be reached at all. This covers the window in between: recording the deletion and removing the aggregate are two calls with no shared transaction, so a tenant can survive its own deletion - and it must not act afterwards, least of all against a realm that is already gone.
     * @throws ApplicationNotSubscribedException The tenant does not use that application, so there is nothing to unsubscribe from.
     */
    public abstract void unsubscribeApplication(final ApplicationId application, final UnsubscribeApplicationService unsubscribeApplicationService) throws TenantAlreadyDeletedException, ApplicationNotSubscribedException;
    
    /**
     * Revokes the tenant's access everywhere at once and disables its realm, without touching its subscriptions.
     *
     * @param reason Why the tenant is being suspended.
     * @param suspendTenantService Disables the tenant's realm in Keycloak.
     *
     * @throws TenantAlreadyDeletedException An operation was attempted on a tenant that has already been deleted. <p> Deleting removes the aggregate as well as recording the fact, so normally the tenant cannot be reached at all. This covers the window in between: recording the deletion and removing the aggregate are two calls with no shared transaction, so a tenant can survive its own deletion - and it must not act afterwards, least of all against a realm that is already gone.
     */
    public abstract void suspendTenant(final SuspensionReason reason, final SuspendTenantService suspendTenantService) throws TenantAlreadyDeletedException;
    
    /**
     * Deletes the tenant for good, removing its realm from Keycloak along with every user and every personal detail it held. <p> This is the erasure path: jtenman itself stores no personal data beyond opaque subject ids, so deleting the realm is what actually removes a person's details from the system. <p> Irreversible, and gated on the tenant being suspended first. That is not bureaucracy: it forces access to be revoked and the revocation to reach every application before anything is destroyed, and it turns an accident into two deliberate steps with a pause between them.
     *
     * @param reason Why the tenant is being deleted.
     * @param deleteTenantService Removes the tenant's realm from Keycloak.
     *
     * @throws TenantAlreadyDeletedException An operation was attempted on a tenant that has already been deleted. <p> Deleting removes the aggregate as well as recording the fact, so normally the tenant cannot be reached at all. This covers the window in between: recording the deletion and removing the aggregate are two calls with no shared transaction, so a tenant can survive its own deletion - and it must not act afterwards, least of all against a realm that is already gone.
     * @throws TenantNotSuspendedException A tenant was deleted while it was still active. Deleting is irreversible, so it is only allowed once access has been revoked and that revocation has reached every application.
     */
    public abstract void deleteTenant(final SuspensionReason reason, final DeleteTenantService deleteTenantService) throws TenantAlreadyDeletedException, TenantNotSuspendedException;
    
    /**
     * Makes a suspended tenant usable again, restoring exactly the subscriptions it had.
     *
     * @param resumeTenantService Enables the tenant's realm in Keycloak again.
     *
     * @throws TenantAlreadyDeletedException An operation was attempted on a tenant that has already been deleted. <p> Deleting removes the aggregate as well as recording the fact, so normally the tenant cannot be reached at all. This covers the window in between: recording the deletion and removing the aggregate are two calls with no shared transaction, so a tenant can survive its own deletion - and it must not act afterwards, least of all against a realm that is already gone.
     */
    public abstract void resumeTenant(final ResumeTenantService resumeTenantService) throws TenantAlreadyDeletedException;
    
}

/**
 * Copyright (C) 2026 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.jtenman.command.core.domain.tenants;

import org.fuin.ddd4j.core.ApplyEvent;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.dsl.cqrs.common.basics.EmailAddress;
import org.fuin.dsl.cqrs.common.exceptions.EntityInStateDeletedException;
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
import org.fuin.jtenman.shared.domain.tenants.TenantAlreadyRegisteredException;
import org.fuin.jtenman.shared.domain.tenants.TenantDeletedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantNotSuspendedException;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.jtenman.shared.domain.tenants.TenantRegisteredEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantResumedEvent;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.fuin.jtenman.shared.domain.tenants.TenantSuspendedEvent;
import org.fuin.jtenman.shared.domain.tenants.UnknownApplicationException;
import org.fuin.objects4j.common.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * A customer of the system, represented by one Keycloak realm, together with the applications it is allowed to use. <p> The subscriptions live here rather than in an aggregate of their own: a subscription has no lifecycle independent of the tenant, and suspending must revoke access to every application at once, which is a single-aggregate invariant. The collection is bounded by the number of applications in the system.
 */
public final class Tenant extends AbstractTenant {

    @SuppressWarnings("NullAway.Init")
    private IssuerUri issuerUri;

    @SuppressWarnings("NullAway.Init")
    private TenantStatus status;

    private final List<ApplicationId> applications = new ArrayList<>();

    private boolean deleted;

    /**
     * Default constructor for loading the aggregate root from history.
     */
    public Tenant() {
        super();
    }

    /**
     * Registers a new tenant and creates its realm in Keycloak. The realm is created under the rights of the signed-in system administrator - no credential of jtenman's own is involved.
     *
     * @param id Unique aggregate identifier, as sent by the command.
     * @param realm Name of the realm to create.
     * @param registerTenantService Creates the tenant's realm in Keycloak.
     *
     * @throws TenantAlreadyRegisteredException A tenant is already registered for that realm. The realm name is the tenant's identity, so it can only be registered once.
     */
    public Tenant(final TenantRealmId id, final RealmName realm, final RegisterTenantService registerTenantService) throws TenantAlreadyRegisteredException {
        super(id);

        // Check preconditions
        Contract.requireArgNotNull("realm", realm);
        Contract.requireArgNotNull("registerTenantService", registerTenantService);

        // Verify business constraints
        if (registerTenantService.realmExists(realm)) {
            throw new TenantAlreadyRegisteredException(realm.asBaseType());
        }

        // The realm is created before the event is applied on purpose. Creating it is the part that can
        // fail; if it does, there is nothing to record - whereas a tenant recorded without a realm would
        // be a tenant nobody can ever reach.
        final IssuerUri createdIssuerUri = registerTenantService.createRealm(realm);

        // Apply events
        apply(TenantRegisteredEvent.builder()
                .entityIdPath(getId())
                .aggregateVersion(getNextApplyVersion())
                .issuerUri(createdIssuerUri)
                .build());
    }

    /**
     * Invites a person to administer the tenant's realm, so the tenant can manage its own users from that point on. <p> A freshly registered tenant has a realm nobody can enter. This is what makes it usable, and it is a separate operation from registering on purpose: creating the realm and inviting a person are two calls to Keycloak with no shared transaction, so folding them together would leave a realm that exists but can never be reached if the second one fails. Separate, the invitation can simply be repeated - which is also what "resend the invitation" needs. <p> <b>No password is ever created, transmitted or stored.</b> The person is created with the required actions to set their own credential and receives a one-time link by email. jtenman therefore never holds a working credential for any tenant.
     *
     * @param email Where to send the invitation. Used to send it and then forgotten - only the resulting subject id becomes part of the event stream.
     * @param inviteAdministratorService Creates the person in the tenant's realm and sends the invitation.
     */
    public final void inviteAdministrator(final EmailAddress email, final InviteAdministratorService inviteAdministratorService) throws EntityInStateDeletedException {
        // Check preconditions
        Contract.requireArgNotNull("email", email);
        Contract.requireArgNotNull("inviteAdministratorService", inviteAdministratorService);

        // Verify business constraints
        requireNotDeleted();

        // The address goes to Keycloak and no further: only the subject id it hands back is applied, so
        // no personal data enters the event stream. The subscriptions go with it because the roles the
        // administrators group has to carry depend on them, and this aggregate is the only place that
        // knows what this tenant is subscribed to.
        final SubjectId invited = inviteAdministratorService.inviteRealmAdministrator(realmName(), email,
                List.copyOf(applications));

        // Apply events
        apply(AdministratorInvitedEvent.builder()
                .entityIdPath(getId())
                .aggregateVersion(getNextApplyVersion())
                .subjectId(invited)
                .build());
    }

    /**
     * Grants the tenant access to an application by creating that application's client and its audience mapper in the tenant's realm. <p> Both halves are needed before the tenant can use the application: the consuming application only sees tenants subscribed to it, and it rejects tokens that do not carry its audience.
     *
     * @param application The application to grant access to.
     * @param subscribeApplicationService Resolves the application in the catalogue and provisions its Keycloak client.
     *
     * @throws UnknownApplicationException An application that is not part of the configured catalogue was named. Which applications exist is deployment configuration, not domain state, so an unknown identifier is a mistake rather than something to record.
     * @throws ApplicationAlreadySubscribedException The tenant is already subscribed to that application. Subscribing twice would create a second Keycloak client for the same purpose.
     */
    public final void subscribeApplication(final ApplicationId application, final SubscribeApplicationService subscribeApplicationService) throws EntityInStateDeletedException, UnknownApplicationException, ApplicationAlreadySubscribedException {
        // Check preconditions
        Contract.requireArgNotNull("application", application);
        Contract.requireArgNotNull("subscribeApplicationService", subscribeApplicationService);

        // Verify business constraints
        requireNotDeleted();
        if (!subscribeApplicationService.isKnownApplication(application)) {
            throw new UnknownApplicationException(application.asBaseType());
        }
        if (applications.contains(application)) {
            throw new ApplicationAlreadySubscribedException(getId().asBaseType(), application.asBaseType());
        }

        subscribeApplicationService.createClient(realmName(), application);

        // Apply events
        apply(ApplicationSubscribedEvent.builder()
                .entityIdPath(getId())
                .aggregateVersion(getNextApplyVersion())
                .application(application)
                .build());
    }

    /**
     * Withdraws the tenant's access to an application and removes that application's client from its realm again. <p> Removing the client matters: dropping the tenant from the application's list alone leaves a realm able to mint tokens carrying that application's audience, which becomes live access again as soon as anything trusts the audience by itself.
     *
     * @param application The application to withdraw access to.
     * @param unsubscribeApplicationService Removes the application's Keycloak client from the tenant's realm.
     *
     * @throws ApplicationNotSubscribedException The tenant does not use that application, so there is nothing to unsubscribe from.
     */
    public final void unsubscribeApplication(final ApplicationId application, final UnsubscribeApplicationService unsubscribeApplicationService) throws EntityInStateDeletedException, ApplicationNotSubscribedException {
        // Check preconditions
        Contract.requireArgNotNull("application", application);
        Contract.requireArgNotNull("unsubscribeApplicationService", unsubscribeApplicationService);

        // Verify business constraints
        requireNotDeleted();
        if (!applications.contains(application)) {
            throw new ApplicationNotSubscribedException(getId().asBaseType(), application.asBaseType());
        }

        unsubscribeApplicationService.removeClient(realmName(), application);

        // Apply events
        apply(ApplicationUnsubscribedEvent.builder()
                .entityIdPath(getId())
                .aggregateVersion(getNextApplyVersion())
                .application(application)
                .build());
    }

    /**
     * Revokes the tenant's access everywhere at once and disables its realm, without touching its subscriptions.
     *
     * @param reason Why the tenant is being suspended.
     * @param suspendTenantService Disables the tenant's realm in Keycloak.
     */
    public final void suspendTenant(final SuspensionReason reason, final SuspendTenantService suspendTenantService) throws EntityInStateDeletedException {
        // Check preconditions
        Contract.requireArgNotNull("reason", reason);
        Contract.requireArgNotNull("suspendTenantService", suspendTenantService);

        // Verify business constraints
        requireNotDeleted();

        // Suspending an already suspended tenant records nothing: the realm is disabled either way, and a
        // second event would only add noise to the audit trail.
        if (status == TenantStatus.SUSPENDED) {
            return;
        }

        suspendTenantService.disableRealm(realmName());

        // Apply events
        apply(TenantSuspendedEvent.builder()
                .entityIdPath(getId())
                .aggregateVersion(getNextApplyVersion())
                .reason(reason)
                .build());
    }

    /**
     * Deletes the tenant for good, removing its realm from Keycloak along with every user and every personal detail it held. <p> This is the erasure path: jtenman itself stores no personal data beyond opaque subject ids, so deleting the realm is what actually removes a person's details from the system. <p> Irreversible, and gated on the tenant being suspended first. That is not bureaucracy: it forces access to be revoked and the revocation to reach every application before anything is destroyed, and it turns an accident into two deliberate steps with a pause between them.
     *
     * @param reason Why the tenant is being deleted.
     * @param deleteTenantService Removes the tenant's realm from Keycloak.
     *
     * @throws TenantNotSuspendedException A tenant was deleted while it was still active. Deleting is irreversible, so it is only allowed once access has been revoked and that revocation has reached every application.
     */
    public final void deleteTenant(final SuspensionReason reason, final DeleteTenantService deleteTenantService) throws EntityInStateDeletedException, TenantNotSuspendedException {
        // Check preconditions
        Contract.requireArgNotNull("reason", reason);
        Contract.requireArgNotNull("deleteTenantService", deleteTenantService);

        // Verify business constraints
        requireNotDeleted();
        if (status != TenantStatus.SUSPENDED) {
            throw new TenantNotSuspendedException(getId().asBaseType());
        }

        deleteTenantService.removeRealm(realmName());

        // Apply events
        apply(TenantDeletedEvent.builder()
                .entityIdPath(getId())
                .aggregateVersion(getNextApplyVersion())
                .reason(reason)
                .build());
    }

    /**
     * Makes a suspended tenant usable again, restoring exactly the subscriptions it had.
     *
     * @param resumeTenantService Enables the tenant's realm in Keycloak again.
     */
    public final void resumeTenant(final ResumeTenantService resumeTenantService) throws EntityInStateDeletedException {
        // Check preconditions
        Contract.requireArgNotNull("resumeTenantService", resumeTenantService);

        // Verify business constraints
        requireNotDeleted();

        // Nothing to resume if it is already active.
        if (status == TenantStatus.ACTIVE) {
            return;
        }

        resumeTenantService.enableRealm(realmName());

        // Apply events
        apply(TenantResumedEvent.builder()
                .entityIdPath(getId())
                .aggregateVersion(getNextApplyVersion())
                .build());
    }

    /**
     * Returns the applications this tenant is allowed to use.
     *
     * @return Immutable copy of the subscribed applications.
     */
    public List<ApplicationId> getApplications() {
        return List.copyOf(applications);
    }

    /**
     * Returns whether the tenant may currently be used.
     *
     * @return Current status.
     */
    public TenantStatus getStatus() {
        return status;
    }

    /**
     * Returns the issuer URI of the tenant's realm.
     *
     * @return Issuer URI.
     */
    public IssuerUri getIssuerUri() {
        return issuerUri;
    }

    /**
     * Returns whether the tenant was deleted.
     *
     * @return TRUE if it is gone.
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Enforces the shared EntityMustNotBeDeletedRule.
     * <p>
     * Deleting a tenant records a fact and removes the realm; it deletes neither the aggregate nor its
     * stream, so a deleted tenant loads and answers commands exactly like any other. This is the only
     * thing that stops it. Left to the repository, the rule would depend on a stream having been
     * removed - and removing it in the handler is what races the projection, so it is not removed at all.
     *
     * @throws EntityInStateDeletedException The tenant is gone.
     */
    private void requireNotDeleted() throws EntityInStateDeletedException {
        if (deleted) {
            throw new EntityInStateDeletedException(new EntityIdPath(getId()));
        }
    }

    /**
     * The realm name is the aggregate's identity, so it is derived rather than stored a second time -
     * two copies of one value are two chances to disagree.
     *
     * @return Name of the tenant's realm.
     */
    private RealmName realmName() {
        return new RealmName(getId().asBaseType());
    }

    /**
     * Handles: TenantRegisteredEvent.
     *
     * @param event Event to handle.
     */
    @Override
    @ApplyEvent
    protected final void handleTenantRegisteredEvent(final TenantRegisteredEvent event) {
        // The genesis event carries the identity. Without this the aggregate replays from history with a
        // null id, and every later operation fails with a NullPointerException far away from the cause.
        setId(event.getEntityIdPath().first());
        this.issuerUri = event.getIssuerUri();
        this.status = TenantStatus.ACTIVE;
    }

    /**
     * Handles: AdministratorInvitedEvent.
     *
     * @param event Event to handle.
     */
    @Override
    @ApplyEvent
    protected final void handleAdministratorInvitedEvent(final AdministratorInvitedEvent event) {
        // Nothing to keep: who administers the realm is Keycloak's state, not the tenant's. The event
        // exists for the audit trail - who was given the keys, and when.
    }

    /**
     * Handles: ApplicationSubscribedEvent.
     *
     * @param event Event to handle.
     */
    @Override
    @ApplyEvent
    protected final void handleApplicationSubscribedEvent(final ApplicationSubscribedEvent event) {
        applications.add(event.getApplication());
    }

    /**
     * Handles: ApplicationUnsubscribedEvent.
     *
     * @param event Event to handle.
     */
    @Override
    @ApplyEvent
    protected final void handleApplicationUnsubscribedEvent(final ApplicationUnsubscribedEvent event) {
        applications.remove(event.getApplication());
    }

    /**
     * Handles: TenantDeletedEvent.
     *
     * @param event Event to handle.
     */
    @Override
    @ApplyEvent
    protected final void handleTenantDeletedEvent(final TenantDeletedEvent event) {
        this.deleted = true;
    }

    /**
     * Handles: TenantSuspendedEvent.
     *
     * @param event Event to handle.
     */
    @Override
    @ApplyEvent
    protected final void handleTenantSuspendedEvent(final TenantSuspendedEvent event) {
        this.status = TenantStatus.SUSPENDED;
    }

    /**
     * Handles: TenantResumedEvent.
     *
     * @param event Event to handle.
     */
    @Override
    @ApplyEvent
    protected final void handleTenantResumedEvent(final TenantResumedEvent event) {
        this.status = TenantStatus.ACTIVE;
    }

}

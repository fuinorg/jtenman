# Security

Authentication, authorization and provisioning for jtenman. Part of the steering documentation - see
[tech.md](tech.md) for the surrounding technology stack.

jtenman is the **control plane**: it decides which realms are tenants of which applications. It is
therefore both the most privileged component of the system and the one that must be locked down hardest.

**Keycloak version: 26.x.** The fine-grained admin permission model was reworked inside this line, so
`org.keycloak:keycloak-admin-client` must be pinned to the same version as the server it administers.

## jtenman itself: one realm, no tenants

jtenman uses the same Keycloak starter as an administered application, but is **not** multi-tenant:

- `org.fuin.cqrs4j.multitenancy=false`
- **no** `WritableTenantContext` bean, so the decoder's `ifPresent` is a no-op and nothing is
  tenant-scoped
- `spring.security.oauth2.resourceserver.jwt.audiences=jtenman-api` - the starter refuses to start
  without it
- **`SingleRealmTenantRepository`** pinned to the administration realm

That last one is not optional. `KeycloakTenantRepository` discovers realms on demand **regardless of the
multitenancy flag**, so without replacing it jtenman would accept a token from any realm of the Keycloak
instance - including a realm it just created for a tenant. The control plane would inherit the exact bug
it exists to fix.

Access is restricted to a `tenant-admin` realm role.

## Provisioning runs as the signed-in administrator

Every Keycloak change jtenman makes - creating a realm, inviting its first administrator, creating an
application's client and audience mapper, disabling a realm - runs with the **caller's own token**,
through the inline `<MethodName>Service` of the aggregate method that needs it. jtenman holds no
credential able to administer Keycloak.

This is the half of the "no technical user" rule that is kept absolutely: an application holding a
credential that can create users or map roles is a categorically larger risk than one that can read a
list of realm names.

## Bringing a tenant to life takes three steps, in order

| Step | Operation | Effect in Keycloak |
| --- | --- | --- |
| 1 | `registerTenant` | creates the realm |
| 2 | `inviteAdministrator` | creates the tenant's first person and sends them a one-time link |
| 3 | `subscribeApplication` | creates that application's client and its audience mapper |

Taking it out of service reverses the order: `suspendTenant` first, then `deleteTenant` - see
[Erasure](#erasure-deletetenant).

**A tenant is not usable until all three have run**, and each failure mode is deliberately visible rather
than silent:

- After step 1 the realm exists but **nobody can enter it**. That is why step 2 is a separate operation
  and not folded into registration: the two are separate Keycloak calls with no shared transaction, so
  combining them would leave a realm that exists and can never be reached if the second half failed.
  Separate, the invitation is simply repeated - which is also what "resend the invitation" needs when a
  mail is lost or the person leaves.
- Without step 3 the tenant's tokens carry only Keycloak's default `account` audience, so the
  application rejects them. "Registered but not subscribed" therefore surfaces as a clean 401, never as
  partial access.

### The invitation never involves a password

`inviteAdministrator` creates the person with the required actions to set their own credential and has
Keycloak send a one-time link. **No password is generated, transmitted, stored or logged at any point** -
so jtenman never holds a working credential for any tenant, and a leak of jtenman's own store yields no
way into a tenant realm. A temporary password would give up exactly that property.

The invited person is placed in a **group** carrying the tenant-administrator role, never granted the
role directly: the same invariant that governs the service accounts above. From that point the tenant
administers its own users, and jtenman is not involved again.

### Only the subject id reaches the event stream

`AdministratorInvitedEvent` records a `SubjectId` - the OpenID Connect `sub`. The email address is a
parameter of the operation, used to send the invitation and then dropped.

This is not incidental. Events are immutable, so an email address written into one becomes personal data
that a deletion request cannot reach; an opaque subject id simply stops resolving to anybody once the
user is removed from Keycloak. The same rule applies to every person referenced anywhere in the model:
**identify people by subject id, resolve names and addresses from Keycloak at read time.**

## Machine-to-machine access uses dedicated roles

The rule is relaxed for flows where **no user exists to act as** - a scheduler polling the tenant list, a
process manager delivering an outbox command. Those use Keycloak client-credentials service accounts,
under tight scope:

- **Dedicated machine roles no human ever holds** - `svc-tenant-read` for the registry pull,
  `svc-command-dispatch` for outbox delivery. A shared role would make "person or scheduler?"
  unanswerable in both authorization and audit.
- **Assigned through their own groups.** A Keycloak service account *is* a user, so granting it a role
  directly would break the invariant that roles are only ever assigned to groups. Each service account
  gets a group carrying exactly its machine role.
- **One service account per purpose**, never shared.
- **A machine role is transport authority, not domain authority.** `svc-command-dispatch` must not read
  as "may perform any command"; the domain decision is taken before the command is enqueued.
- **Least privilege**: no `realm-management`, no `manage-users`, no admin API for any service account.
  Secrets from a secret store, not from `application.yml`.

Audit records both the originating user's subject id and the delivering service account.

The tenant-list endpoint is authenticated this way too, even though the list carries no secrets - realm
names and issuer URIs are public and OIDC discovery documents world-readable. The risk it guards against
is *integrity*, a spoofed list injecting a rogue tenant, which is also why **TLS is mandatory** for it
regardless of the token.

## Realm names are constrained, and jtenman enforces it

`org.fuin.ddd4j.core.TenantId` accepts **2-10 characters** matching `^[a-z][a-z|0-9|_]*[a-z|0-9]$` -
lowercase, digits and underscore, **no hyphens**, starting with a letter. The Keycloak starter derives a
`TenantId` from the realm segment of the issuer while decoding a token, so a realm outside that range
makes decoding throw - a **500 in every consuming application**, not a clean rejection.

`RealmName` and `TenantRealmId` enforce exactly those rules, so `registerTenant` refuses a bad name here,
in the control plane, where a person sees the message. A customer called "Müller-Schmidt GmbH" cannot have
a realm named after them.

## Two layers guard an administered application

Neither is redundant:

| Layer           | Answers                                         | Enforced by                      |
|-----------------|-------------------------------------------------|----------------------------------|
| The tenant list | Is this realm a tenant **of this application**? | the replica an application polls |
| The audience    | Was this token issued **for this application**? | `JwtAudiencesValidator`          |

A realm subscribed to application A can mint tokens carrying A's audience; it cannot use them against B,
because B's list does not contain it. Conversely a realm on the list of B without B's audience mapper is
rejected too. That is why `unsubscribeApplication` removes the Keycloak client as well as the list entry:
leaving the client behind keeps a realm able to mint tokens with that audience, which becomes live access
again the moment anything trusts the audience by itself.

## Revocation works in two layers

|                              | Effect                                      | Timing                      |
|------------------------------|---------------------------------------------|-----------------------------|
| Realm disabled in Keycloak   | no new logins, sessions killed              | immediate                   |
| Dropped from the tenant list | applications reject tokens from that issuer | within one refresh interval |

The second stops **already-issued** tokens: the issuer becomes unknown, so validation fails regardless of
the token's remaining lifetime. It only works against a cqrs-4-java build that evicts
`JwtTenantIssuerValidator`'s and `JwtTenantKeySelector`'s caches on `TenantRemovedEvent`; an older build
caches a resolved issuer forever and revokes nothing.

`suspendTenant` is the emergency lever: it removes the tenant from every application at once **without
touching its subscriptions**, so `resumeTenant` restores exactly the previous set.

## Erasure: deleteTenant

`deleteTenant` removes the realm from Keycloak along with every user and every personal detail it held.
It is **irreversible** and it is **gated on the tenant being suspended first** - `MustBeSuspended`, which
fails with `TenantNotSuspendedException`.

That gate is the safety property, not paperwork. Suspending first revokes access and lets the revocation
propagate to every application before anything is destroyed, and it turns a mistaken click into two
deliberate steps with a pause in between.

**This is what makes erasure work at all.** jtenman stores nothing about a person except an opaque
`SubjectId`, so there is no personal data in the event stream to erase; deleting the realm removes the
data itself, and the surviving history of what was provisioned resolves to nobody. Suspending alone does
*not* achieve that - a disabled realm still holds its users indefinitely.

### delete or purge - and why `deleteTenant` uses delete

ddd4j's `Repository` offers both, and they differ in one way that matters here:

| | Effect on the stream | Realm name afterwards |
| --- | --- | --- |
| `Repository.delete(...)` | soft delete | **reusable** |
| `Repository.purge(...)` | tombstone | **burned forever** |

`purge` exists precisely for erasure, and it is tempting to reach for it here. **`deleteTenant` uses
`delete`.**

Be clear about what that does and does not buy, because it is less than it looks. A soft-deleted stream
still exists, so `Repository.add` with the same identifier is refused - **a realm name is retired once
its tenant is deleted, either way.** That is verified behaviour, not a guess: re-running the example
against the same realm answers "A tenant is already registered". `delete` is still the right choice, but
for the narrower reason that `purge` tombstones the stream and would additionally destroy the
provisioning history, while gaining nothing in return.

Nothing is lost by choosing `delete`, because **the personal data was never in the stream to begin with**:
jtenman records only opaque `SubjectId`s, and `removeRealm` deletes the actual data in Keycloak. What the
soft-deleted stream keeps is the provisioning history - who was registered, subscribed and deleted, and
when - which is exactly the audit trail you want to survive.

The handler removes the aggregate as well as recording the event, and **every operation additionally
carries a `MustNotBeDeleted` rule**. Both, not either:

- Removing the aggregate is what normally stops a deleted tenant being reached at all.
- The rule covers the window between the two - recording the deletion and removing the aggregate are
  separate calls with no shared transaction, so a tenant can outlive its own deletion. Without the rule,
  the only thing stopping it from acting would be Keycloak answering 404 for a realm that no longer
  exists: infrastructure accidentally enforcing a domain invariant, and reporting it as a server error.

A deleted tenant therefore answers `TenantAlreadyDeletedException` to everything, whichever of the two
caught it.

`purge` is the right call where the aggregate itself holds the personal data. That is not this aggregate.

## Development escape hatches

`docker-compose.yml` runs Keycloak in `start-dev` with a literal `admin/admin` bootstrap account and no
TLS. Development only, and labelled as such. Nothing in this repository may ship a credential that works
anywhere else.

## Open decisions

- **Transactional consistency of provisioning.** A Keycloak call can succeed while the event append
  fails, or the reverse - there is no shared transaction. If retries prove necessary, the `process` side's
  outbox is the place for them.
- **When a suspended tenant should be deleted.** `deleteTenant` exists and is the erasure path, but
  nothing drives it: an operator decides. If a retention period ("delete N months after suspension")
  is ever required, that is a job for the `process` side, not for a human to remember.

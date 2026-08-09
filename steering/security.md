# Security

Authentication, authorization and provisioning for jtenman. Part of the steering documentation - see
[tech.md](tech.md) for the surrounding technology stack.

jtenman is the **control plane**: it decides which realms are tenants of which applications. It is
therefore both the most privileged component of the system and the one that must be locked down hardest.

**Keycloak version: 26.x.** The fine-grained admin permission model was reworked inside this line, so the
major matters: `org.keycloak:keycloak-admin-client` must come from the **same major** as the server it
administers.

It must *not* be pinned to the server's exact version. Keycloak releases the server and the Java client
on deliberately decoupled lifecycles: the client stays in the `26.0.x` stream and is built to work
against the whole of major 26, so `26.0.12` administers a server anywhere from `26.0.0` to `26.7.1`.
Expect the two numbers to diverge - a client that looks "behind" the server is the intended state, not
drift to be corrected.

## jtenman itself: one realm, no tenants

jtenman uses the same Keycloak starter as an administered application, but is **not** multi-tenant:

- `org.fuin.cqrs4j.multitenancy=false`
- **no** `WritableTenantContext` bean, so the decoder's `ifPresent` is a no-op and nothing is
  tenant-scoped
- `spring.security.oauth2.resourceserver.jwt.audiences=jtenman-api` - the starter refuses to start
  without it
- **`SingleRealmTenantRepository`** pinned to the administration realm, declared by
  `SingleRealmTenantAutoConfiguration` in `internal`

That last one is not optional. `KeycloakTenantRepository` discovers realms on demand **regardless of the
multitenancy flag**, so without replacing it jtenman would accept a token from any realm of the Keycloak
instance - including a realm it just created for a tenant. The control plane would inherit the exact bug
it exists to fix.

It is an **auto-configuration**, not something each deployable imports: every one (combined,
command/server, query/server) reaches it through its jtenman starter, and one added later gets it
without anyone remembering to. A forgotten import would leave a silently discovering
repository, which is the one failure this is here to prevent. It is ordered `before`
`KeycloakSecurityAutoConfiguration`, whose own repository bean is
`@ConditionalOnMissingBean(JwtTenantRepository.class)` and has to see this one already registered to back
off. On start each server logs the one issuer it accepts, so the trust boundary of a running instance is
readable from its log:

```
Tenant trust boundary pinned to the single issuer 'http://localhost:8180/realms/master' - every other
realm of this Keycloak instance is rejected
```

`SingleRealmTenantAutoConfigurationTest` asserts the part that would otherwise regress unnoticed: loaded
beside the keycloak starter, exactly one `JwtTenantRepository` remains and it is the single-realm one.

**`jtenman-internal` is, as the name says, internal.** An application administered *by* jtenman needs the
jtenman-fed repository of `jtenman-starter`; pinning it to one realm would make it reject every one of
its own tenants. That is why the keycloak starter is a `provided` dependency there rather than a compile
one, so it cannot travel to a consumer.

## Access is restricted to a `tenant-admin` realm role

`ControlPlaneSecurityAutoConfiguration` in `internal` is the only `SecurityFilterChain` a jtenman
deployable has:

| Path            | Requires                            | In practice                          |
|-----------------|-------------------------------------|--------------------------------------|
| `/cmd/**`       | `tenant-admin`                      | every command                        |
| `/view/**`      | `tenant-admin` or `svc-tenant-read` | the tenant list an application polls |
| everything else | an authenticated caller, no role    | the actuator, the OpenAPI UI         |

Without it a deployable falls back to Spring Boot's default resource-server chain, which asks no more
than `anyRequest().authenticated()`. In the control plane that is close to no authorization at all: any
person with an account in the administration realm and a token carrying the `jtenman-api` audience could
create realms, invite administrators and delete tenants. The single-realm repository above answers *which
realm* may speak to jtenman; this answers *who inside it* may do what. Both are needed.

Three things about it are easy to get wrong and are therefore fixed here:

- **Only realm roles work.** `KeycloakJwtAuthenticationConverter` maps `realm_access.roles` and ignores
  `resource_access.*.roles`, so `tenant-admin` granted as a client role is invisible - a 403 against a
  Keycloak setup that looks correct. This is the same rule as for the service accounts below, seen from
  the other side.
- **The command paths are matched for every HTTP method**, not for `POST` only. A method-scoped matcher
  would let any other verb on the same path fall through to the merely-authenticated rule beneath it.
- **CSRF is off and no session is created.** Every caller authenticates with a bearer token per request,
  so there is no cookie to ride on. The defaults would reject every `POST /cmd/{type}` for a missing CSRF
  token, which is not a security property here but an outage.

`svc-tenant-read` is admitted to the read side although the service account carrying it does not exist
yet - the tenant list is what an administered application polls, and that pull is a machine identity (see
below). Until it is provisioned the read side is reachable with a `tenant-admin` token, which is what
`doc/example/run-example.sh` uses.

The chain is `@ConditionalOnMissingBean(SecurityFilterChain.class)`, so an application-supplied chain
replaces it whole. That is what the `*ApplicationIT` classes use to boot without a Keycloak, and it
is the one way this rule can be lost: a permit-all chain left outside test scope would silently take over
- the application still starts, still validates tokens, still looks configured, and no longer checks a
role. **`ArchitectureTest`, in each deployable, refuses to let that reach production sources.** It matches a class that calls both `anyRequest()` and `permitAll()` - neither half alone,
because the production chain calls `anyRequest()` too and a legitimate chain may permit a single path -
and it has no exception: jtenman ships no development profile, so there is nowhere such a chain belongs
outside a test. One copy per deployable rather than one shared, because each scans its own classpath.

Three tests carry the rule itself. `ControlPlaneSecurityAutoConfigurationTest` drives real requests
through the real chain - no token is a 401, a token without the role a 403, a client role a 403.
`ControlPlaneAuthorizationIT` repeats it over HTTP against the deployable's own controllers, which
matters because `TenantController` is regenerated on every build and its mapping is not something review
can rely on staying put. `ArchitectureTest` guards the escape hatch above.

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

## The replica an application polls: `jtenman-starter`

The first layer above is a *replica*, and `jtenman-starter` is what maintains it. An administered
application adds that one module, names itself and points it at jtenman:

```yaml
jtenman:
  registry:
    url: https://jtenman.example.com
    application: billing
```

It declares a `JwtTenantRepository` fed by the tenant list, replacing the Keycloak starter's
`KeycloakTenantRepository`. That default discovers realms on demand and accepts every one of them, which
is no admission control and no revocation - the hole this whole system exists to close, seen from the
consuming end.

Four properties of it are security decisions rather than implementation detail:

- **It fails closed.** Before the first successful pull it accepts nobody. An application that cannot ask
  which realms are its tenants must not fall back to trusting any.
- **A stale list eventually stops being trusted.** A list that cannot be refreshed is served for
  `max-staleness` (5 minutes by default) and then dropped. A replicated revocation list is only as good
  as its age, and serving a snapshot of unbounded age means a suspended tenant keeps working for as long
  as the outage lasts. Setting the property to zero trades that guarantee for availability; it is the
  operator's choice, and it has to be an explicit zero.
- **A tenant leaving the list is announced**, which is what evicts `JwtTenantIssuerValidator`'s and
  `JwtTenantKeySelector`'s caches. That eviction is the whole of the second revocation layer below.
  The announcement carries only the tenant id and needs no Keycloak call, so a tenant that disappears is
  still announced while the identity provider is down - which is exactly when it matters.
- **No request thread performs I/O.** The list and each tenant's OpenID Connect configuration are fetched
  on the refresh thread; an issuer that is not on the list is refused by a map lookup. The discovering
  repository cannot do this - it learns of an issuer only when a token carrying it arrives - and pays for
  it with a negative cache to stop a slow Keycloak occupying every request thread.

The pull itself needs the `svc-tenant-read` role, so the application declares a `TenantListAuthProvider`.
It is a seam rather than a property because the token is short lived and has to be obtained: a static
token in a configuration file is the long-lived credential the rules below exist to avoid. The default
provider sends nothing, jtenman answers 401 and the list stays empty - loud and closed.

**An unreachable jtenman does not stop an application from starting.** Refusing would tie every
administered application's rollout to the control plane being up at that moment, and the application is
not dangerous without the list - it accepts nobody until a pull succeeds.

## Revocation works in two layers

|                              | Effect                                      | Timing                      |
|------------------------------|---------------------------------------------|-----------------------------|
| Realm disabled in Keycloak   | no new logins, sessions killed              | immediate                   |
| Dropped from the tenant list | applications reject tokens from that issuer | within one refresh interval |

The second stops **already-issued** tokens: the issuer becomes unknown, so validation fails regardless of
the token's remaining lifetime. Two things have to hold for it, and both do - `jtenman-starter` announces
the removal, and the cqrs-4-java build in use evicts `JwtTenantIssuerValidator`'s and
`JwtTenantKeySelector`'s caches on `TenantRemovedEvent`. An older cqrs-4-java caches a resolved issuer
forever and revokes nothing, so the version matters as much as the wiring.

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

### Deleting a tenant does not delete its stream

ddd4j's `Repository` offers two ways to remove an aggregate's stream:

| | Effect on the stream | Realm name afterwards |
| --- | --- | --- |
| `Repository.delete(...)` | soft delete | retired (`add` is still refused) |
| `Repository.purge(...)` | tombstone | burned forever |

**`deleteTenant` calls neither.** It records `TenantDeletedEvent` and updates the aggregate, exactly like
every other operation. The realm - and with it the personal data - is gone from Keycloak; the stream stays.

Deleting the stream in the handler is not merely unnecessary, it is **wrong, and it was a real bug here**.
The read model is a projection that catches up asynchronously, so a stream deleted in the same call that
appended the deletion event is gone before the projection ever reads that event. The result was a deleted
tenant still listed as `ACTIVE` by every application polling `list-by-application` - precisely the state
this system exists to prevent. The rule is general:

> **Never remove an aggregate's stream in the command handler that ends its life.** Record the event,
> update, return. If the streams themselves ever have to go, that is a separate reaper that deletes them
> only once every projection has passed the deletion event - the positions it needs are already persisted
> by `QryProjectionService.updateProjectionPosition`.

Nothing is lost by keeping the stream, because **the personal data was never in it to begin with**:
jtenman records only opaque `SubjectId`s, and `removeRealm` erased the actual data in Keycloak. What
remains is the provisioning history - who was registered, subscribed and deleted, and when - which is
exactly the audit trail you want to survive. `purge` would be the right call where the aggregate itself
holds the personal data. That is not this aggregate.

What stops a deleted tenant from acting is therefore the model, not the absence of a stream: **every
operation carries the shared `EntityMustNotBeDeletedRule`** and a deleted tenant answers
`EntityInStateDeletedException`
to all of them. That is where the invariant belongs. Had it been left to stream removal, the only thing
stopping a deleted tenant would be Keycloak answering 404 for a realm that no longer exists -
infrastructure accidentally enforcing a domain invariant, and reporting it as a server error.

A realm name is still retired once its tenant is deleted: the stream exists, so `Repository.add` with the
same identifier is refused. That is verified behaviour, not a guess - re-running the example against the
same realm answers "A tenant is already registered".

## Development escape hatches

`docker-compose.yml` runs Keycloak in `start-dev` with a literal `admin/admin` bootstrap account and no
TLS. Development only, and labelled as such. Nothing in this repository may ship a credential that works
anywhere else.

`doc/example/setup-keycloak.sh` grants that bootstrap account `tenant-admin` - and does it the way the
rules above require even though it is a development script: it creates the realm role, creates a
`tenant-admins` group carrying it, and puts the user in the group rather than granting the role
directly. A script that took the shortcut would be the one thing a reader copies.

## Open decisions

- **Transactional consistency of provisioning.** A Keycloak call can succeed while the event append
  fails, or the reverse - there is no shared transaction. If retries prove necessary they belong in an
  outbox, and jtenman has no process side to hold one - see below.
- **When a suspended tenant should be deleted.** `deleteTenant` exists and is the erasure path, but
  nothing drives it: an operator decides. A retention period ("delete N months after suspension") would
  need something scheduled, which is the second thing that would justify a process side.
- **jtenman has no process side at all.** Its provisioning is synchronous - every Keycloak change happens
  inside the command that asked for it - so there is nothing to coordinate and no outbox to deliver. The
  modules were removed rather than left empty. The two decisions above are what would bring them back;
  until then jtenman needs no `svc-command-dispatch` service account, because it dispatches nothing.

# Security plan

The security work still ahead, and why it is in this order. Companion to [security.md](security.md),
which states the *rules*; this file states the *sequence*. When a step lands, move its substance into
`security.md` as a statement of what is enforced, and strike it from here — this file should shrink over
time, not grow.

Status as of 2026-08-09.

## Where things actually stand

Checkable from source rather than from another document:

|                          | State                                                                                                                                                                                        |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Trust boundary           | **Done.** `SingleRealmTenantAutoConfiguration` pins jtenman to the administration realm; `SingleRealmTenantAutoConfigurationTest` proves the discovering repository backs off.                |
| `tenant-admin` role      | **Done.** `ControlPlaneSecurityAutoConfiguration` is the only filter chain a deployable has, and `ArchitectureTest` keeps a permit-all chain out of production sources.                       |
| Consumer-facing starter  | **Not started.** `jtenman-query-starter` is KurrentDB projection wiring; it contains no `JwtTenantRepository` implementation, and the generated `TenantServiceRestClient` has no consumer.    |
| Service accounts         | **Not started.** No client-credentials registration, no `svc-tenant-read`, no `svc-command-dispatch`, and `KeycloakTenantAdapter.createClient` cannot yet create the confidential client.     |

Two quick checks:

```bash
grep -rn "JwtTenantRepository" --include="*.java" query   # nothing yet - the starter is projection wiring
grep -rn "svc-tenant-read\|client_credentials" --include="*.java" .   # only the role constant
```

## 1. The consumer-facing starter

The README's promise — "an application adds `jtenman-query-starter`, points it at jtenman and names
itself" — is a promise, not code. That module contains only KurrentDB projection wiring and no
`JwtTenantRepository` implementation. The client half is generated (`TenantServiceRestClient` in
`query/api`) but nothing anywhere instantiates it, which is the honest measure of how far this is from
done: the list is served and never read.

This is the step that turns admission control and revocation from a design into a mechanism. Until it
exists, the two layers described in `security.md` under "Two layers guard an administered application"
are one and a half: the audience is enforced by every application, but nothing consumes the tenant list,
so dropping a tenant from it revokes nothing anywhere. An administered application pins itself to a
single realm instead, which is correct only while it has one tenant.

Two things the module has to get right, both already visible in the code it will replace:

- It feeds `JwtTenantRepository`, so a tenant that disappears from the list has to evict the caches in
  `JwtTenantIssuerValidator` and `JwtTenantKeySelector`. Without that eviction an already-issued token
  stays valid for its full lifetime and the second revocation layer in `security.md` does not exist.
- The keycloak starter is a `provided` dependency of `starter-common` precisely so that jtenman's own
  single-realm pinning cannot travel to a consumer through this module. Whatever the new starter
  exposes must keep that separation: an application administered *by* jtenman needs the fed repository,
  never the pinned one.

## 2. Service accounts

`security.md` § "Machine-to-machine access uses dedicated roles" states the rule; nothing implements it.
Two accounts, each in its own group, no `realm-management`, secrets from a secret store rather than from
`application.yml`:

| Account               | For                                            |
|-----------------------|------------------------------------------------|
| `svc-tenant-read`     | the registry pull an administered application does |
| `svc-command-dispatch`| outbox delivery of a command                   |

`svc-tenant-read` is already admitted by the filter chain, so provisioning the account is the whole of
that half — see `JtenmanRoles`.

**jtenman cannot provision the client it needs, and that is the part to decide before deployment
day.** `KeycloakTenantAdapter.createClient` creates an application's client as a *public* one
(`setPublicClient(true)`, `setDirectAccessGrantsEnabled(false)`, standard flow only). A service account
needs a confidential client with `serviceAccountsEnabled=true`, and for `private_key_jwt` also
`clientAuthenticatorType = "client-jwt"` plus the public key — as a JWKS URL if the application can serve
one, otherwise a configured certificate. Either `KeycloakTenantAdapter` gains a second client — which
also means extending `ApplicationCatalogue.Entry`, today just `id`/`displayName`/`clientId`/`audience` —
or the operator creates it outside the jtenman flow. Decide which; do not discover it at deployment time.

Prefer `private_key_jwt` over a client secret where the counterparty supports it: it removes the shared
secret, so there is nothing both sides know and nothing replayable from a captured request. A private key
still has to be placed until signing moves into a secret store, which is a smaller problem, not none.

## Carry-overs

- Adopt `AuditedRepository` in the seven command handlers — the same audit trail for free.
- **All four `*ApplicationIT` classes still run permit-all.** They declare their own
  `SecurityFilterChain` so they can reach `/actuator/health` without a Keycloak. That is the documented
  escape hatch and `ArchitectureTest` fences it, but it does mean those four prove only that the context
  starts — `ControlPlaneAuthorizationIT` is the one that proves anything about who may call what. Worth
  revisiting if a fifth deployable ever copies the pattern without adding an authorization IT beside it.

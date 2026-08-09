# Security plan

The security work still ahead, and why it is in this order. Companion to [security.md](security.md),
which states the *rules*; this file states the *sequence*. When a step lands, move its substance into
`security.md` as a statement of what is enforced, and strike it from here — this file should shrink over
time, not grow.

Status as of 2026-08-09.

## Where things actually stand

Checkable from source rather than from another document:

|                         | State                                                                                                                                                                                     |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Trust boundary          | **Done.** `SingleRealmTenantAutoConfiguration` pins jtenman to the administration realm; `SingleRealmTenantAutoConfigurationTest` proves the discovering repository backs off.             |
| `tenant-admin` role     | **Done.** `ControlPlaneSecurityAutoConfiguration` is the only filter chain a deployable has, and `ArchitectureTest` keeps a permit-all chain out of production sources.                    |
| Consumer-facing starter | **Done.** `jtenman-starter` replicates the tenant list into a `JwtTenantRepository`, announces what changed, and fails closed before the first pull and after `max-staleness`.             |
| Service accounts        | **Not started.** No client-credentials registration, no `svc-tenant-read`, no `svc-command-dispatch`, and `KeycloakTenantAdapter.createClient` cannot yet create the confidential client.  |

One quick check:

```bash
grep -rn "svc-tenant-read\|client_credentials" --include="*.java" .   # only the role constant
```

## 1. Service accounts

`security.md` § "Machine-to-machine access uses dedicated roles" states the rule; nothing implements it.
Two accounts, each in its own group, no `realm-management`, secrets from a secret store rather than from
`application.yml`:

| Account                | For                                                |
|------------------------|----------------------------------------------------|
| `svc-tenant-read`      | the registry pull an administered application does |
| `svc-command-dispatch` | outbox delivery of a command                       |

Both halves of `svc-tenant-read` are waiting for it and neither needs new code: the filter chain already
admits the role (see `JtenmanRoles`), and `jtenman-starter` already has the seam that supplies the token
— a `TenantListAuthProvider` bean. Provisioning the account and writing that provider is the whole step.
Until it exists the pull is unauthenticated, answers 401, and every administered application accepts
nobody. **That is the one thing between the consumer-facing starter and a deployment that works.**

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
- **The `*ApplicationIT` classes still run permit-all.** They declare their own
  `SecurityFilterChain` so they can reach `/actuator/health` without a Keycloak. That is the documented
  escape hatch and `ArchitectureTest` fences it, but it does mean they prove only that the context
  starts — `ControlPlaneAuthorizationIT` is the one that proves anything about who may call what. Worth
  revisiting if another deployable ever copies the pattern without adding an authorization IT beside it.
- **The tenant list has no end-to-end test against a running jtenman.** `TenantRegistryAutoConfigurationTest`
  drives the real pull over real HTTP, but against a stand-in serving a captured response. The capture was
  verified by hand against a live `combined` — including that an unsubscribed tenant stops being accepted
  within one refresh interval — and that check is worth turning into an IT once there is a service account
  to authenticate it with.

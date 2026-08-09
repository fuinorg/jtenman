# Security plan

The security work still ahead, and why it is in this order. Companion to [security.md](security.md),
which states the *rules*; this file states the *sequence*. When a step lands, move its substance into
`security.md` as a statement of what is enforced, and strike it from here — this file should shrink over
time, not grow.

Status as of 2026-08-09.

## Where things actually stand

Checkable from source rather than from another document:

|                         | State                                                                                                                                                                          |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Trust boundary          | **Done.** `SingleRealmTenantAutoConfiguration`, now from `cqrs-4-java-springboot-security`, pins jtenman to the administration realm.                                                                                       |
| `tenant-admin` role     | **Done.** The shared chain from `cqrs-4-java-springboot-security` is the only one a deployable has, and its rules are `cqrs4j.security.rules` in `application.yml`; `ArchitectureTest` keeps a permit-all chain out of production sources.         |
| Consumer-facing starter | **Done.** `jtenman-starter` replicates the tenant list into a `JwtTenantRepository` and fails closed before the first pull and after `max-staleness`.                           |
| `svc-tenant-read`       | **Done.** `ClientCredentialsTenantListAuthProvider` fetches the list as the service account; `setup-keycloak.sh` provisions role, group, confidential client and audience mapper. |
| `svc-command-dispatch`  | **Not applicable.** It authenticates outbox delivery and jtenman has no process side. The rule stands in `security.md` for applications that do.                               |

**No security step is outstanding**, and the audit trail is in place: every event names the caller who
caused it (`security.md`, "Who did it is recorded"). What follows is the tail — things worth doing, none
of them blocking a deployment.

## Carry-overs

- **The `*ApplicationIT` classes still run permit-all.** They declare their own `SecurityFilterChain` so
  they can reach `/actuator/health` without a Keycloak. That is the documented escape hatch and
  `ArchitectureTest` fences it, but it does mean they prove only that the context starts —
  `ControlPlaneAuthorizationIT` is the one that proves anything about who may call what. Worth revisiting
  if another deployable ever copies the pattern without adding an authorization IT beside it.
- ~~The consumer side has no automated end-to-end test.~~ **Done.** `TenantRegistryE2EIT` in the `e2e`
  module runs the whole chain against a real Keycloak and a real jtenman: service account, audience
  mapper, role through a group, replication, revocation within one refresh interval, and the account
  being refused on `/cmd`.
- **`private_key_jwt` instead of a client secret.** `svc-tenant-read` uses a shared secret today. Spring
  reaches the asymmetric variant with `client-authentication-method: private_key_jwt` and a parameters
  converter, needing no change in `jtenman-starter`; the remaining problem is placing the key, which is a
  secret-store question rather than a jtenman one.

## Still open, and not scheduled here

Both are in `security.md` under "Open decisions" and would bring back the process side that was removed:
retries for provisioning that half-succeeded, and a retention period after which a suspended tenant is
deleted.

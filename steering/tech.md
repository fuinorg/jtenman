# Tech Stack

## Project Structure

Maven multi-module project, deliberately the same shape as the applications jtenman administers. The root
is a `pom`-packaging aggregator/parent managing dependencies via BOMs, with one aggregator module per
CQRS concern, each split into `api`, `core`, `server` and `starter`:

```
jtenman                 (pom, root parent)
├── steering            (non-Maven)          # Product, technology and security documentation
├── model               (jar)                # CQRS model files and SrcGen4J generator - NOT published
├── shared              (jar)                # code shared across the command and query sides
├── internal            (jar)                # Spring Boot auto-configuration shared by jtenman's own starters
├── query               (pom, aggregator)    # read side
│   ├── api             (jar)                # public query contracts - published
│   ├── core            (jar)                # projection logic
│   ├── server          (jar)                # runnable server for the query side
│   └── starter         (jar)                # auto-configuration for jtenman's own read side
├── command             (pom, aggregator)    # write side
│   ├── api             (jar)                # command messages - published
│   ├── core            (jar)                # the Tenant aggregate
│   ├── server          (jar)
│   └── starter         (jar)
├── starter             (jar)                # what a CONSUMER adds - published
├── combined            (jar)                # both sides in one deployable
└── e2e                 (jar)                # end-to-end test of the two together - NOT published
```

`command/api` and `query/api` are generated-only; their `src/main/java` exists with a `.gitkeep` so
hand-written code can be added later without a structural change.

Every module carries a `README.md` saying in a sentence or two what it is.

### Publishing

The DSL is private: nothing outside jtenman imports the model, so `model` is **not deployed**
(`maven-deploy-plugin` skipped) and all `.cqrs` files live in `model/private/`. Published:
`jtenman-shared`, `jtenman-command-api`, `jtenman-query-api`, `jtenman-starter`.

## Naming Conventions

- **Modules:** `jtenman-<concern>-<area>` (e.g. `jtenman-command-api`); the folder carries only the last
  part to keep it short.
- **Java packages:** `org.fuin.jtenman.<context>.<area>.<layer>`.
- **Maven coordinates:** `org.fuin:jtenman:1.0.0-SNAPSHOT`.

## Backend

- **Language:** Java 25 (the `model` module pins 21 - Xtext/EMF are not validated on 25 yet)
- **Framework:** Spring Boot 3.5
- **Security:** Spring Security - OAuth2 Resource Server (JWT) against Keycloak, wired by
  `org.fuin.cqrs4j:cqrs-4-java-springboot-keycloak-starter` - see [security.md](security.md)
- **Persistence:** Spring Data JPA (Hibernate); Query=H2, Process=PostgreSQL, Command=KurrentDB
- **Keycloak administration:** `org.keycloak:keycloak-admin-client`, matched to the **major** of the
  server being administered, not to its exact version - see [security.md](security.md)
- **Build:** Maven
- **Ports:** `909x`, so jtenman runs beside the applications it administers (`808x`) - `combined` 9090,
  `command/server` 9091, `query/server` 9092

## Multi-tenancy: jtenman has none

jtenman is the control plane, so it is **single-realm**: `org.fuin.cqrs4j.multitenancy=false`, no
`WritableTenantContext` bean, and `SingleRealmTenantRepository` pinning the trust boundary to one realm.
Without that last part the auto-configured `KeycloakTenantRepository` would discover and accept every
realm of the Keycloak instance - the exact hole jtenman exists to close. See [security.md](security.md).

## Testing

- **Unit Tests:** JUnit 5 + Mockito
- **Integration Tests:** classes named `*IT` run in `verify` via failsafe, so `mvn test` needs no
  container runtime. Containers come from `TestHelper` in `org.fuin.cqrs4j:cqrs-4-java-test-helper`.
  Each deployable has one that boots the real Spring context against a real KurrentDB and
  asserts the Actuator health endpoint reports `UP`. Only the event store is a container: the read model
  is in-memory H2, and each IT replaces the Keycloak-backed security chain with a permit-all one, so no
  identity provider is needed to reach `/actuator/health`.
- **Manual end-to-end:** [`doc/example`](../doc/example) posts every command against a locally running
  jtenman using a token from the development Keycloak, and provisions real realms, clients and users.
  It is the acceptance test the ITs cannot be: the ITs prove the deployables boot, this proves they do
  the work.

## Common Commands

```bash
# Build all modules
mvn clean install

# Start the combined deployable (port 9090)
mvn -pl :jtenman-combined spring-boot:run

# Regenerate from the .cqrs model
mvn -pl model process-sources

# Run unit tests (no container runtime needed)
mvn test

# Run the integration tests too (requires a running Docker/Podman)
mvn verify
```

### Error Prone / NullAway

`.mvn/jvm.config` carries the `--add-exports jdk.compiler/...` flags Error Prone needs on a modern JDK.
Without it the compiler fails with `IllegalAccessError ... cannot access class BasicJavacTask`.

## CQRS Model Files (`.cqrs`)

The `model` module holds the SrcGen4J DDD/CQRS DSL files that Java code is generated from
(grammar: [fuinorg/ddd-cqrs-dsl](https://github.com/fuinorg/ddd-cqrs-dsl)). The conventions and gotchas
are those of any project on this stack; the two that bit while scaffolding jtenman:

- **`Text` is not a common type.** Use `String`, or declare a value object (jtenman uses
  `SuspensionReason`).
- **A `base String` aggregate-id generates an abstract + final pair with `// TODO` stubs**, unlike
  `base UUID`, which generates a self-contained class. `TenantRealmId` is therefore hand-written under
  `shared/src/main/java` and must stay in sync with the constraints of `RealmName`.

### Keep a transaction inside one aggregate

**A change that has to happen as a unit belongs inside a single aggregate.** Only where that is genuinely
impossible should it be spread over several steps, and then it is a **process manager** that coordinates
them - never a command handler quietly doing two things and hoping both land.

An aggregate is the transactional boundary: everything it decides and records commits together. The
moment a change needs a second write - another aggregate, an external system, a second call to the store -
there is no shared transaction any more, and every failure between the two is a state somebody has to
handle. Modelling it inside the aggregate is not a style preference; it is the difference between an
invariant that holds and one that usually holds.

Where the boundary cannot be moved - jtenman has to talk to Keycloak, and Keycloak does not join a
transaction - two rules follow:

- **Do the failing part first, record afterwards.** `registerTenant` creates the realm and only then
  applies its event: if the realm is not created there is nothing to record, whereas a recorded tenant
  without a realm can never be reached.
- **State the invariant in the model, not only in the sequence.** `deleteTenant` removes the realm and
  records the deletion; what stops a deleted tenant from acting afterwards is the shared `EntityMustNotBeDeletedRule`
  every operation carries, not the disappearance of anything. Sequencing makes the normal path right; the
  rule is what holds when a later call arrives anyway.
- **A delete command deletes nothing but the external resource.** Never call `repository.delete(..)` or
  `purge(..)` from a command handler - record the event and `update(..)`, exactly like every other
  operation. The read model catches up asynchronously, so a stream removed in the same call that appended
  the deletion event is gone before the projection reads it, and the read model keeps serving the deleted
  aggregate forever. If streams themselves must go, that is a separate reaper that runs only once every
  projection has passed the deletion event (`QryProjectionService.updateProjectionPosition` persists the
  positions it needs) - never the handler.

If the coordination grows past that - several aggregates, retries, compensation - it is a process manager
with an outbox, and no longer something to solve inside a handler.

### Verifying models

```bash
java -jar <base-path>/ddd-cqrs-dsl/maven/console/target/ddd-cqrs-dsl-console.jar model
```

Pass the **directory**, not a single file, so cross-file references resolve. Exit code `1` on any error.
Use a jar built from the current grammar - a stale one rejects valid models.

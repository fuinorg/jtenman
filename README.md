# jtenman

<img src="doc/logo.svg" alt="" width="110">

**J**ava **ten**ant **man**ager - the tenant control plane for applications built
on [cqrs-4-java](https://github.com/fuinorg/cqrs-4-java).

System administrators use it to spin up a tenant once for the whole system: it creates the tenant's
Keycloak realm, records which applications the tenant may use, and provisions each of those applications'
Keycloak clients. Applications do not administer tenants; they replicate the resulting list and use it to
decide which token issuers they trust.

> :information_source: **[`jtenman-starter`](starter/README.md) is the only module you add to your own
> application.** Everything else in this repository is jtenman itself - its aggregate, its projection,
> its deployables - and none of it belongs on your class path. `jtenman-shared` and `jtenman-query-api`
> are published too, but only because the starter brings them along.

## Integrating it

**1. Add the starter.**

```xml
<dependency>
    <groupId>org.fuin</groupId>
    <artifactId>jtenman-starter</artifactId>
    <version>${jtenman-starter.version}</version>
</dependency>
```
(Define a `jtenman-starter.version` variable with latest available version)

**2. Point it at jtenman and name yourself.** The `application` is your id in jtenman's catalogue, and it
decides which tenants you get - naming somebody else's id replicates their list. Neither setting has a
default.

```yaml
jtenman:
  registry:
    url: https://jtenman.example.com
    application: billing
```

**3. Point it at your service account.** jtenman requires a role on the tenant list, so the pull needs a
token. Name a `spring.security.oauth2.client.registration` entry and the starter fetches one as your
`svc-tenant-read` account:

```yaml
jtenman:
  registry:
    client-registration-id: jtenman
```

An operator creates that account - a confidential Keycloak client in the administration realm, with an
audience mapper and the `svc-tenant-read` role granted through a group. jtenman cannot create it: it
provisions Keycloak with the caller's own token and holds no credential of its own.
`doc/example/setup-keycloak.sh` does it for local development and prints the configuration to copy.

**4. Get registered.** An administrator adds your application to jtenman's catalogue and subscribes each
tenant to it with `subscribeApplication`, which also creates your Keycloak client and its audience
mapper. Until that has run for a tenant, its tokens carry only Keycloak's default audience and you reject
them - a clean 401 rather than partial access.

That is the whole integration. The starter supplies a `JwtTenantRepository` fed by jtenman's tenant list,
replacing the realm-discovering default, so everything downstream - issuer validation, key selection,
per-tenant datasource routing, projections - keeps working unchanged. It refreshes on a loop, so a tenant
jtenman drops stops being accepted within one refresh interval, and it fails closed: no list, nobody
accepted.

Two independent checks then guard every request: **jtenman** says which realms are tenants of this
application, and the **audience** in the token says it was issued for this application.

See [starter/README.md](starter/README.md) for the settings and the failure modes.

## Why it exists

Without a control plane, an application using the cqrs-4-java Keycloak starter accepts **any** realm of
its Keycloak instance as a tenant, discovered on demand, and never drops one again. That means no
admission control and no revocation. This is the authoritative answer to "which realms are tenants,
and of which applications" - so an application can both refuse an unknown realm and stop accepting a
tenant that was suspended, without a restart.

## Modules

Every module has a `README.md` of its own; this is the map.

| Module     | Description                                                                          |
|------------|--------------------------------------------------------------------------------------|
| `model`    | The `.cqrs` DSL sources and the SrcGen4J generator. Private - never published.        |
| `shared`   | Value objects, events and ids shared by all three sides.                              |
| `internal` | Spring Boot auto-configuration shared by jtenman's own deployables. Never a consumer's. |
| `command`  | Write side: the `Tenant` aggregate and its commands.                                  |
| `query`    | Read side: the tenant projection and the contract applications poll.                  |
| `starter`  | **The one module an administered application adds.**                                  |
| `combined` | Both sides in one deployable - the normal way to run jtenman.                         |
| `e2e`      | End-to-end test of an administered application against a running jtenman.             |

Published: `jtenman-starter`, the one you add; `jtenman-shared` and `jtenman-query-api`, which it brings
with it; and `jtenman-command-api`, for a client that sends jtenman its commands rather than consuming
its tenants.

## Building

```bash
mvn clean install          # build everything
mvn -pl model process-sources   # regenerate from the .cqrs model
```

Requires JDK 25. The `model` module pins Java 21, because Xtext/EMF are not yet validated on 25.

## Running locally

```bash
podman compose up -d       # KurrentDB, PostgreSQL and Keycloak
mvn -pl :jtenman-combined spring-boot:run
```

Ports are `909x` so jtenman can run beside the applications it administers, which use `808x`:
`combined` 9090, `command/server` 9091, `query/server` 9092.

## Documentation

- [steering/product.md](steering/product.md) - what jtenman is for
- [steering/tech.md](steering/tech.md) - technology stack and conventions
- [steering/security.md](steering/security.md) - authentication, authorization, provisioning
- [steering/security-plan.md](steering/security-plan.md) - the security work still ahead
- [doc/example/README.md](doc/example/README.md) - the life of a tenant, end to end

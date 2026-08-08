# jtenman

<img src="doc/logo.svg" alt="" width="110">

**J**ava **ten**ant **man**ager - the tenant control plane for applications built
on [cqrs-4-java](https://github.com/fuinorg/cqrs-4-java).

System administrators use it to spin up a tenant once for the whole system: it creates the tenant's
Keycloak realm, records which applications the tenant may use, and provisions each of those applications'
Keycloak clients. Applications do not administer tenants; they replicate the resulting list and use it to
decide which token issuers they trust.

## Why it exists

Without a control plane, an application using the cqrs-4-java Keycloak starter accepts **any** realm of
its Keycloak instance as a tenant, discovered on demand, and never drops one again. That means no
admission control and no revocation. This is the authoritative answer to "which realms are tenants,
and of which applications" - so an application can both refuse an unknown realm and stop accepting a
tenant that was suspended, without a restart.

## How an application uses it

An application adds `jtenman-query-starter`, points it at jtenman and names itself. The starter supplies
a `JwtTenantRepository` that replaces the realm-discovering default, so everything downstream - issuer
validation, key selection, per-tenant datasource routing, projections - keeps working unchanged.

Two independent checks then guard every request: **jtenman** says which realms are tenants of this
application, and the **audience** in the token says it was issued for this application.

## Modules

| Module           | Description                                                                    |
|------------------|--------------------------------------------------------------------------------|
| `model`          | The `.cqrs` DSL sources and the SrcGen4J generator. Private - never published. |
| `shared`         | Value objects, events and ids shared by all three sides.                       |
| `starter-common` | Spring Boot auto-configuration shared by the three starters.                   |
| `command`        | Write side: the `Tenant` aggregate and its commands.                           |
| `query`          | Read side: the tenant projection and the contract applications poll.           |
| `process`        | Process managers.                                                              |
| `combined`       | All three sides in one deployable - the normal way to run jtenman.             |

Published artifacts: `jtenman-shared`, `jtenman-command-api`, `jtenman-query-api` and
`jtenman-query-starter`.

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
`combined` 9090, `command/server` 9091, `query/server` 9092, `process/server` 9093.

## Documentation

- [steering/product.md](steering/product.md) - what jtenman is for
- [steering/tech.md](steering/tech.md) - technology stack and conventions
- [steering/security.md](steering/security.md) - authentication, authorization, provisioning

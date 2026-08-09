# jtenman-starter

The one module an administered application adds: it supplies a `JwtTenantRepository` fed by jtenman's
tenant list, replacing the realm-discovering default of the cqrs-4-java Keycloak starter.

```xml
<dependency>
    <groupId>org.fuin</groupId>
    <artifactId>jtenman-starter</artifactId>
</dependency>
```

```yaml
jtenman:
  registry:
    url: https://jtenman.example.com
    application: billing        # this application's id in jtenman's catalogue
```

Those two have no default. The rest do: `refresh-interval` (30s), `max-staleness` (5m),
`connect-timeout` and `read-timeout` (5s each).

jtenman requires a role on the tenant list, so the pull needs a token. Name a client registration and
the starter obtains one as the `svc-tenant-read` service account:

```yaml
jtenman:
  registry:
    client-registration-id: jtenman

spring:
  security:
    oauth2:
      client:
        registration:
          jtenman:
            client-id: billing-svc
            client-secret: ${JTENMAN_SVC_TENANT_READ_SECRET}
            authorization-grant-type: client_credentials
        provider:
          jtenman:
            token-uri: https://keycloak.example.com/realms/master/protocol/openid-connect/token
```

The token URI is the **administration realm**, not a tenant's - the account is this application's
identity against the control plane. It needs `spring-security-oauth2-client` on the class path, which is
an optional dependency here. Declare a `TenantListAuthProvider` bean instead to obtain the token some
other way. With neither, the pull is unauthenticated and jtenman answers 401.

Behaviour worth knowing before deploying it:

- A tenant jtenman drops stops being accepted within one refresh interval.
- It fails closed - no list, nobody accepted - and a list that stops refreshing is dropped after
  `max-staleness`. Set that to `0` to trust the last known list indefinitely.
- An unreachable jtenman does not stop the application from starting.
- No request thread ever performs I/O; the list and the tenants' signing keys are fetched on the
  refresh thread.

See [../steering/security.md](../steering/security.md) for the rules behind all of this.

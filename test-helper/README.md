# jtenman-test-helper

`StubJtenman` - jtenman's tenant-list endpoint as a small HTTP server, so an application using
`jtenman-starter` can test admission and revocation without running jtenman, its event store, its
database and a Keycloak.

```xml
<dependency>
    <groupId>org.fuin</groupId>
    <artifactId>jtenman-test-helper</artifactId>
    <version>${jtenman-starter.version}</version>
    <scope>test</scope>
</dependency>
```

```java
try (StubJtenman jtenman = StubJtenman.start()) {

    jtenman.subscribe("billing", "acme", URI.create("https://keycloak/realms/acme"));
    // start the application with jtenman.registry.url = jtenman.url()

    jtenman.unsubscribe("billing", "acme");   // revocation: gone within one refresh interval
    jtenman.answerWith(503);                  // outage: the staleness bound decides how long the
                                              // previous list keeps being trusted
    jtenman.requireBearerToken();             // 401 unless the pull sends the service account's token
}
```

The rows are real `TenantDetails` objects serialized the way the starter reads them, not hand-written
JSON - the value objects are single-field wrappers, so whether a realm arrives as `"acme"` or as
`{"value":"acme"}` decides whether the pull works at all. `StubJtenmanTest` pins that shape against a
capture of a real answer, and `jtenman-starter`'s own `StubJtenmanUsableTest` runs the production
reading path against the stub, so the two cannot drift apart unnoticed.

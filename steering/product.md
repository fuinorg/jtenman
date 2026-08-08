# jtenman

"jtenman" is the **tenant control plane** for a system of applications built on cqrs-4-java: the one
place where a system administrator registers a tenant and decides which applications that tenant may use.

It is infrastructure, not a product feature. Its users are system administrators; no end user of any
administered application ever reaches it.

## Features

- Register a tenant, creating its Keycloak realm
- Subscribe a tenant to one or more applications, provisioning each application's Keycloak client and
  audience mapper in that realm
- Unsubscribe from an application again, removing that client
- Suspend and resume a tenant, disabling and re-enabling its realm
- Invite the tenant's first administrator, who then manages the tenant's own users
- Delete a suspended tenant for good, removing its realm and with it every personal detail
  it held - the erasure path
- Serve the resulting list per application, so each application can replicate it and decide which token
  issuers it trusts

## Non-features

- **No user management.** Users belong to their tenant's realm and are managed there.
- **No UI, for now.** jtenman is driven by commands through the generic command endpoint.
- **No tenants of its own.** jtenman is the control plane; it runs single-realm.

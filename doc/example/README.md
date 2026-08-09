# Example: the life of a tenant

Walks one tenant through every operation jtenman has - register, invite an administrator, subscribe to an
application, unsubscribe, suspend, resume, suspend again, delete - by posting the JSON bodies in
[`commands/`](commands) to the generic command endpoint.

There is no magic in it. A client gets a token from Keycloak and does `POST /cmd/{type}`; the script just
does that eight times and prints what came back.

The second suspend is not a typo: `deleteTenant` refuses to run against an active tenant, so resuming has
to be undone before the tenant can be deleted. That is the `MustBeSuspended` rule, visible in the run.

## Run it

```bash
podman compose up -d                       # KurrentDB, PostgreSQL, Keycloak    (repository root)
./doc/example/setup-keycloak.sh            # once: the client, the role and the group
mvn -pl :jtenman-combined spring-boot:run  # jtenman on 9090                    (repository root)

./doc/example/run-example.sh
```

Needs `curl` and `jq`. Everything is overridable by environment variable:

| Variable                              | Default                 |                                            |
|---------------------------------------|-------------------------|--------------------------------------------|
| `KEYCLOAK_URL`                        | `http://localhost:8180` | as started by `docker-compose.yml`         |
| `KEYCLOAK_REALM`                      | `master`                | the realm jtenman itself trusts            |
| `KEYCLOAK_CLIENT`                     | `jtenman-cli`           | created by `setup-keycloak.sh` - see below |
| `REALM`                               | a fresh random one      | the realm the run creates                  |
| `KEYCLOAK_USER` / `KEYCLOAK_PASSWORD` | `admin` / `admin`       | the dev bootstrap account                  |
| `JTENMAN_URL`                         | `http://localhost:9090` | the `combined` deployable                  |
| `ROLE` / `GROUP`                      | `tenant-admin` / `tenant-admins` | `setup-keycloak.sh` only - see below |

## Step by step, if you would rather do it by hand

**1. Get a token** - from `jtenman-cli`, **not** from Keycloak's built-in `admin-cli`. jtenman validates
the `aud` claim, and only this client emits the `jtenman-api` audience. Run `./setup-keycloak.sh` first if
it does not exist yet; using `admin-cli` here answers
`401 ... The aud claim is not valid`.

The same script also puts `admin` in the `tenant-admins` group, which carries the **`tenant-admin` realm
role**. Every `/cmd/**` call needs it and so does the read side, so a token without it is a `403` - see
below.

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d client_id=jtenman-cli -d username=admin -d password=admin -d grant_type=password \
  | jq -r .access_token)
```

**2. Post a command.** The type in the path is the `eventType` inside the body:

```bash
curl -i -X POST http://localhost:9090/cmd/RegisterTenantCommand \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary @doc/example/commands/01-register-tenant.json
```

**3. Subscribe the tenant to an application.** Registering creates the tenant; it does not connect it to
anything. Until this runs, the tenant belongs to no application and step 4 returns `[]`.

Get a **fresh token first** - the realm was created after your last one was issued, so that token carries
no rights over it and Keycloak answers 403 (see below):

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d client_id=jtenman-cli -d username=admin -d password=admin -d grant_type=password \
  | jq -r .access_token)

curl -i -X POST http://localhost:9090/cmd/SubscribeApplicationCommand \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary @doc/example/commands/03-subscribe-application.json
```

**4. Read the tenants of an application** through the query side. The `application` here is the id from
the catalogue in `application.yml` - `melkheftken` - not the tenant's realm:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:9090/view/tenant/list-by-application?application=melkheftken' | jq .
```

The read model is a projection, so allow a few seconds after the subscribe before it appears.

That last call is the contract a consuming application polls, so it is the one worth watching: the tenant
appears after `subscribeApplication` and disappears again after the unsubscribe, the suspend or the
delete - each for a different reason.

## About the command bodies

They were produced by serialising real command objects with the application's own `ObjectMapper`, so the
field names are the actual wire format rather than a guess. Two things are easy to get wrong by hand:

- **`entity-id-path`** is `"TENANT acme"` - the entity type, a space, then the realm. Not just the
  realm, and the type is **upper case**: it is the `EntityType` constant, not the class name.
  `run-example.sh` rewrites it along with `realm`, so a run uses its own realm rather than the one in the
  files.
- **`event-id`** must be a UUID and must differ per call - each command is a distinct message. The files
  carry a real one so they are valid on their own, but `run-example.sh` **replaces `event-id` and
  `event-timestamp` with fresh values before every post**. That is what a client does, and it is what
  makes the script re-runnable: posting the stored ids again would replay the identical commands.

`correlation-id`, `causation-id` and `aggregate-version` may stay `null`. Setting `aggregate-version`
turns the call into an optimistic-locking check, which is what a UI would do to detect a concurrent edit.

## The password grant, and why it is only here

`steering/security.md` forbids the password grant for applications, which must use Authorization Code
with PKCE. This script is the exception and stays one: a shell script has no browser to redirect to, it
talks only to the local development Keycloak from `docker-compose.yml`, and the token never leaves the
machine. Do not copy this into anything that ships.

## Four things the run will teach you

**A fresh token per command, not one for the whole run.** Keycloak grants the roles that administer a
realm *per realm*, so a token minted before `registerTenant` created the realm carries no rights over it -
and the very next command fails with 403. Anything that creates a realm and then administers it has to
re-authenticate in between.

**A separate client, because the audience is validated.** jtenman rejects a token that was not issued for
it, and Keycloak's built-in `admin-cli` does not emit the `jtenman-api` audience. That is what
`setup-keycloak.sh` creates: a `jtenman-cli` client with an audience mapper. Without it every call is a
401, which is the audience check doing its job.

**401 and 403 answer different questions.** A 401 means the token was not accepted at all - wrong
audience, wrong realm, expired, or none sent. A 403 means it was accepted and the caller is simply not a
`tenant-admin`. The role has to be a **realm** role: the Keycloak starter maps `realm_access.roles` and
ignores client roles, so granting `tenant-admin` on the `jtenman-cli` client instead leaves every call a
403 against a Keycloak setup that looks right in the UI.

**A realm name is retired once its tenant is deleted.** `deleteTenant` removes the realm in Keycloak, but
the aggregate and its event stream stay - marked deleted, answering every further command with
`EntityInStateDeletedException`. Registering the same realm again is therefore refused with "A tenant is
already registered". The script invents a fresh realm per run - set `REALM` to pin it.

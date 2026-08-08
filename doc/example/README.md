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
./doc/example/setup-keycloak.sh            # once: the client whose tokens jtenman accepts
mvn -pl :jtenman-combined spring-boot:run  # jtenman on 9090                    (repository root)

./doc/example/run-example.sh
```

Needs `curl` and `jq`. Everything is overridable by environment variable:

| Variable | Default | |
| --- | --- | --- |
| `KEYCLOAK_URL` | `http://localhost:8180` | as started by `docker-compose.yml` |
| `KEYCLOAK_REALM` | `master` | the realm jtenman itself trusts |
| `KEYCLOAK_CLIENT` | `jtenman-cli` | created by `setup-keycloak.sh` - see below |
| `REALM` | a fresh random one | the realm the run creates |
| `KEYCLOAK_USER` / `KEYCLOAK_PASSWORD` | `admin` / `admin` | the dev bootstrap account |
| `JTENMAN_URL` | `http://localhost:9090` | the `combined` deployable |

## Step by step, if you would rather do it by hand

**1. Get a token.**

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password \
  | jq -r .access_token)
```

**2. Post a command.** The type in the path is the `eventType` inside the body:

```bash
curl -i -X POST http://localhost:9090/cmd/RegisterTenantCommand \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary @doc/example/commands/01-register-tenant.json
```

**3. Read the tenants of an application** through the query side:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:9090/view/tenant/list-by-application?application=melkheftken' | jq .
```

That last call is the contract a consuming application polls, so it is the one worth watching: the tenant
appears after `subscribeApplication` and disappears again after the unsubscribe, the suspend or the
delete - each for a different reason.

## About the command bodies

They were produced by serialising real command objects with the application's own `ObjectMapper`, so the
field names are the actual wire format rather than a guess. Two things are easy to get wrong by hand:

- **`entity-id-path`** is `"Tenant acme"` - the entity type, a space, then the realm. Not just the realm.
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

## Three things the run will teach you

**A fresh token per command, not one for the whole run.** Keycloak grants the roles that administer a
realm *per realm*, so a token minted before `registerTenant` created the realm carries no rights over it -
and the very next command fails with 403. Anything that creates a realm and then administers it has to
re-authenticate in between.

**A separate client, because the audience is validated.** jtenman rejects a token that was not issued for
it, and Keycloak's built-in `admin-cli` does not emit the `jtenman-api` audience. That is what
`setup-keycloak.sh` creates: a `jtenman-cli` client with an audience mapper. Without it every call is a
401, which is the audience check doing its job.

**A realm name is retired once its tenant is deleted.** `deleteTenant` removes the realm and the
aggregate, but the aggregate's event stream is soft-deleted rather than tombstoned, so registering the
same realm again is refused with "A tenant is already registered". The script therefore invents a fresh
realm per run - set `REALM` to pin it.

#!/usr/bin/env bash
#
# Walks a tenant through its whole life against a running jtenman: register, invite an administrator,
# subscribe to an application, unsubscribe, suspend, resume and delete.
#
# It fetches a token from a pre-started Keycloak and posts the JSON bodies in doc/example/commands to
# the generic command endpoint. Nothing here is jtenman-specific plumbing - it is exactly what any
# client does: get a token, POST /cmd/{type}.
#
# See doc/example/README.md for the prerequisites.

set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-master}"
KEYCLOAK_CLIENT="${KEYCLOAK_CLIENT:-jtenman-cli}"
KEYCLOAK_USER="${KEYCLOAK_USER:-admin}"
KEYCLOAK_PASSWORD="${KEYCLOAK_PASSWORD:-admin}"
JTENMAN_URL="${JTENMAN_URL:-http://localhost:9090}"

# Realm the example creates. A fresh one per run by default, because the last command deletes the tenant
# and a deleted realm name is retired: its event stream still exists (soft deleted), so registering the
# same name again is refused. Set REALM to repeat against a specific one.
# Built without a pipeline on purpose: "set -o pipefail" turns the SIGPIPE from something like
# "tr ... | head -c 7" into an immediate exit, and the script dies before it does anything.
REALM="${REALM:-t$(printf '%07x' $(((RANDOM << 15 | RANDOM) % 268435456)))}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMANDS="${HERE}/commands"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }

require() {
  command -v "$1" >/dev/null 2>&1 || { red "Required tool not found: $1"; exit 1; }
}
require curl
require jq

# uuidgen is not everywhere; fall back to the kernel's UUID source, then to python.
new_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen
  elif [ -r /proc/sys/kernel/random/uuid ]; then
    cat /proc/sys/kernel/random/uuid
  else
    python3 -c 'import uuid; print(uuid.uuid4())'
  fi
}

# Fetches a token and puts it in TOKEN.
#
# Called again before every command, and that is not belt-and-braces. Keycloak grants the roles that
# administer a realm per realm, so a token minted BEFORE "acme" existed carries no rights over it - the
# very next command, run with that token, fails with 403. Anything that creates a realm and then
# administers it has to get a fresh token in between.
#
# The password grant is used ONLY here, and only against the local development Keycloak from
# docker-compose.yml. It is forbidden in the applications themselves - see steering/security.md, which
# requires Authorization Code + PKCE. A shell script has no browser to redirect to, and this token never
# leaves the machine.
fetch_token() {
  local response
  response="$(curl -s -X POST \
    "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    -d "client_id=${KEYCLOAK_CLIENT}" \
    -d "username=${KEYCLOAK_USER}" \
    -d "password=${KEYCLOAK_PASSWORD}" \
    -d 'grant_type=password')" || true

  TOKEN="$(printf '%s' "${response}" | jq -r '.access_token // empty')"
  if [ -z "${TOKEN}" ]; then
    red "Could not get a token. Keycloak said:"
    printf '%s\n' "${response}" | jq . 2>/dev/null || printf '%s\n' "${response}"
    echo
    echo "Is Keycloak up?          podman compose up -d keycloak"
    echo "Is the client created?   ./doc/example/setup-keycloak.sh"
    exit 1
  fi
}

bold "1. Fetching a token from Keycloak"
echo "   ${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM} (client ${KEYCLOAK_CLIENT}, user ${KEYCLOAK_USER})"
fetch_token
green "   got a token (${#TOKEN} characters)"
echo

bold "2. Checking jtenman is up"
# The health endpoint is behind the same security chain as everything else, so it needs the token too.
if ! curl -sf -H "Authorization: Bearer ${TOKEN}" "${JTENMAN_URL}/actuator/health" >/dev/null; then
  red "jtenman is not answering at ${JTENMAN_URL}"
  echo "Start it with:   mvn -pl :jtenman-combined spring-boot:run"
  exit 1
fi
green "   ${JTENMAN_URL} is healthy"
echo

# Posts one command file. The command type in the URL is the "eventType" inside the body - the endpoint
# uses it to find the deserializer, and the body carries it again for the event store.
#
# The "event-id" and "event-timestamp" in the file are overwritten with a fresh value per call. That is
# what a real client does - each command is a distinct message - and it is what makes this script
# re-runnable: posting the stored ids again would replay the very same commands.
post_command() {
  local file="$1"
  local type
  type="$(jq -r '.eventType' "${file}")"

  bold "-> ${type}   ($(basename "${file}"))"

  # Fresh token: see fetch_token above - a realm created by the previous command is not covered by a
  # token that predates it.
  fetch_token

  local payload
  payload="$(jq --arg id "$(new_uuid)" --arg ts "$(date -Iseconds)" --arg realm "${REALM}" \
    '.["event-id"] = $id
     | .["event-timestamp"] = $ts
     | .["entity-id-path"] = "Tenant " + $realm
     | (if has("realm") then .realm = $realm else . end)' "${file}")"

  local http_code body response
  response="$(curl -s -w $'\n%{http_code}' -X POST \
    "${JTENMAN_URL}/cmd/${type}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H 'Content-Type: application/json' \
    --data-binary "${payload}")"
  http_code="$(printf '%s' "${response}" | tail -n1)"
  body="$(printf '%s' "${response}" | sed '$d')"

  if [ "${http_code}" = "200" ] || [ "${http_code}" = "201" ] || [ "${http_code}" = "204" ]; then
    green "   ${http_code}"
    [ -n "${body}" ] && printf '%s\n' "${body}" | jq . 2>/dev/null || true
  else
    red "   ${http_code}"
    printf '%s\n' "${body}" | jq . 2>/dev/null || printf '%s\n' "${body}"
    if printf '%s' "${body}" | grep -q "NoSuchBeanDefinition"; then
      echo
      red "   No handler is registered for this command yet."
      echo "   The command handlers and the Keycloak adapter are not implemented - see"
      echo "   doc/example/README.md, section 'What does not work yet'."
    fi
    return 1
  fi
  echo
}

bold "3. Running the commands in order for realm ${REALM}"
echo
FAILED=0
for file in "${COMMANDS}"/*.json; do
  post_command "${file}" || FAILED=1
done

if [ "${FAILED}" -ne 0 ]; then
  echo
  red "Some commands failed - see above."
  exit 1
fi

green "All commands accepted."

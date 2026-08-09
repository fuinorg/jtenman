#!/usr/bin/env bash
#
# Prepares the realm jtenman itself trusts: the client whose tokens it accepts, and the role that lets a
# caller do anything.
#
# Two independent checks have to pass before a command is executed, and each fails differently:
#
#   1. The audience. jtenman validates "aud", so a token is only accepted if it was issued FOR jtenman.
#      The built-in admin-cli client does not do that - its tokens carry Keycloak's default audiences and
#      jtenman answers 401. This creates a "jtenman-cli" client with an audience mapper emitting
#      "jtenman-api".
#   2. The role. Every /cmd/** call requires the "tenant-admin" REALM role, and the read side requires it
#      too. Without it the token is perfectly valid and every command answers 403.
#
# The role is granted through a group, never directly to the user - see steering/security.md. It has to
# be a realm role: the Keycloak starter maps realm_access.roles and ignores client roles, so the same
# role granted on the client would be invisible and every call would stay a 403.
#
# Run once after starting Keycloak. Safe to re-run: every step does nothing if it is already there.

set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
ADMIN_REALM="${ADMIN_REALM:-master}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
CLIENT_ID="${CLIENT_ID:-jtenman-cli}"
AUDIENCE="${AUDIENCE:-jtenman-api}"
ROLE="${ROLE:-tenant-admin}"
GROUP="${GROUP:-tenant-admins}"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }

api() {
  local method="$1" path="$2"
  shift 2
  curl -s -X "${method}" "${KEYCLOAK_URL}/admin/realms/${ADMIN_REALM}${path}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'Content-Type: application/json' \
    "$@"
}

bold "Getting an admin token"
ADMIN_TOKEN="$(curl -s -X POST \
  "${KEYCLOAK_URL}/realms/${ADMIN_REALM}/protocol/openid-connect/token" \
  -d client_id=admin-cli -d "username=${ADMIN_USER}" -d "password=${ADMIN_PASSWORD}" \
  -d grant_type=password | jq -r '.access_token // empty')"

if [ -z "${ADMIN_TOKEN}" ]; then
  red "Could not authenticate as ${ADMIN_USER} at ${KEYCLOAK_URL}"
  echo "Is Keycloak up?   podman compose up -d keycloak"
  exit 1
fi
green "   ok"

bold "1. The '${CLIENT_ID}' client in realm '${ADMIN_REALM}'"
EXISTING="$(api GET "/clients?clientId=${CLIENT_ID}" | jq -r '. | length')"

if [ "${EXISTING}" != "0" ]; then
  green "   already exists"
else
  # A public client with the password grant enabled, because a shell script has no browser to redirect
  # to. This is the development-only exception the security documentation calls out; the applications
  # themselves use Authorization Code with PKCE and this client is not one of them.
  api POST "/clients" -o /dev/null -w '' \
    -d "$(jq -n --arg cid "${CLIENT_ID}" --arg aud "${AUDIENCE}" '{
          clientId: $cid,
          enabled: true,
          publicClient: true,
          standardFlowEnabled: false,
          directAccessGrantsEnabled: true,
          protocolMappers: [{
            name: "audience",
            protocol: "openid-connect",
            protocolMapper: "oidc-audience-mapper",
            config: {
              "included.custom.audience": $aud,
              "access.token.claim": "true",
              "id.token.claim": "false"
            }
          }]
        }')"
  green "   created, emitting audience '${AUDIENCE}'"
fi

bold "2. The '${ROLE}' realm role"
if api GET "/roles/${ROLE}" | jq -e '.name? // empty' >/dev/null; then
  green "   already exists"
else
  api POST "/roles" -o /dev/null -w '' \
    -d "$(jq -n --arg name "${ROLE}" '{
          name: $name,
          description: "Administers the jtenman control plane: registers, subscribes, suspends and deletes tenants"
        }')"
  green "   created"
fi
ROLE_JSON="$(api GET "/roles/${ROLE}" | jq -c '{id, name}')"

bold "3. The '${GROUP}' group carrying it"
GROUP_ID="$(api GET "/groups?search=${GROUP}&exact=true" | jq -r --arg name "${GROUP}" \
  '[.[] | select(.name == $name)] | first | .id // empty')"
if [ -z "${GROUP_ID}" ]; then
  api POST "/groups" -o /dev/null -w '' -d "$(jq -n --arg name "${GROUP}" '{name: $name}')"
  GROUP_ID="$(api GET "/groups?search=${GROUP}&exact=true" | jq -r --arg name "${GROUP}" \
    '[.[] | select(.name == $name)] | first | .id // empty')"
  green "   created"
else
  green "   already exists"
fi

if [ -z "${GROUP_ID}" ]; then
  red "Could not create or find the group '${GROUP}'"
  exit 1
fi

# Idempotent on Keycloak's side: re-adding a mapping the group already has is a no-op.
api POST "/groups/${GROUP_ID}/role-mappings/realm" -o /dev/null -w '' -d "[${ROLE_JSON}]"
green "   carries the realm role '${ROLE}'"

bold "4. '${ADMIN_USER}' in that group"
USER_ID="$(api GET "/users?username=${ADMIN_USER}&exact=true" | jq -r 'first | .id // empty')"
if [ -z "${USER_ID}" ]; then
  red "Could not find the user '${ADMIN_USER}' in realm '${ADMIN_REALM}'"
  exit 1
fi
# PUT is idempotent - the user is simply already a member on a second run.
api PUT "/users/${USER_ID}/groups/${GROUP_ID}" -o /dev/null -w ''
green "   done"

echo
echo "Now run:   ./doc/example/run-example.sh"

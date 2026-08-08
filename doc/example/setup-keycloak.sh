#!/usr/bin/env bash
#
# Creates the Keycloak client the example needs, in the realm jtenman itself trusts.
#
# jtenman validates the "aud" claim, so a token is only accepted if it was issued FOR jtenman. The
# built-in admin-cli client does not do that - its tokens carry Keycloak's default audiences and jtenman
# answers 401. This creates a "jtenman-cli" client with an audience mapper that emits "jtenman-api".
#
# Run once after starting Keycloak. Safe to re-run: it does nothing if the client already exists.

set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
ADMIN_REALM="${ADMIN_REALM:-master}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
CLIENT_ID="${CLIENT_ID:-jtenman-cli}"
AUDIENCE="${AUDIENCE:-jtenman-api}"

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }

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

bold "Creating the '${CLIENT_ID}' client in realm '${ADMIN_REALM}'"
EXISTING="$(curl -s -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  "${KEYCLOAK_URL}/admin/realms/${ADMIN_REALM}/clients?clientId=${CLIENT_ID}" | jq -r '. | length')"

if [ "${EXISTING}" != "0" ]; then
  green "   already exists - nothing to do"
  exit 0
fi

# A public client with the password grant enabled, because a shell script has no browser to redirect to.
# This is the development-only exception the security documentation calls out; the applications
# themselves use Authorization Code with PKCE and this client is not one of them.
curl -s -o /dev/null -w '' -X POST \
  "${KEYCLOAK_URL}/admin/realms/${ADMIN_REALM}/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
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
echo
echo "Now run:   ./doc/example/run-example.sh"

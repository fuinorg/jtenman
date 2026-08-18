/**
 * Copyright (C) 2026 Michael Schnell. All rights reserved.
 * http://www.fuin.org/
 * <p>
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library. If not, see http://www.gnu.org/licenses/.
 */
package org.fuin.jtenman.e2e;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Does to a throwaway Keycloak what {@code doc/example/setup-keycloak.sh} does to the development one:
 * the client an administrator signs in with, the {@code tenant-admin} role, and the service account an
 * administered application polls its tenant list with.
 * <p>
 * Deliberately a second implementation of that script rather than a call to it. The script is
 * documentation an operator follows by hand, and a test that shells out to it would be testing bash. What
 * has to agree between the two is the <b>result</b> - a confidential client whose service account holds
 * {@code svc-tenant-read} through a group and whose tokens carry the {@code jtenman-api} audience - and
 * the test asserts that result rather than the steps.
 * <p>
 * Every role goes through a group here too, for the same reason the script does it: this is the shape a
 * reader copies.
 */
final class KeycloakFixture {

    /** Client an administrator signs in with. Public, password grant - the test has no browser. */
    static final String CLI_CLIENT = "jtenman-cli";

    /** Confidential client of the administered application, holding the machine role. */
    static final String SVC_CLIENT = "melkheftken-svc";

    static final String AUDIENCE = "jtenman-api";

    static final String TENANT_ADMIN = "tenant-admin";

    static final String SVC_TENANT_READ = "svc-tenant-read";

    private static final String ADMIN_REALM = "master";

    private final RestClient rest;

    private final String baseUrl;

    private final String adminUser;

    private final String adminPassword;

    private String svcClientSecret;

    KeycloakFixture(final String baseUrl, final String adminUser, final String adminPassword) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl==null");
        this.adminUser = Objects.requireNonNull(adminUser, "adminUser==null");
        this.adminPassword = Objects.requireNonNull(adminPassword, "adminPassword==null");
        this.rest = RestClient.create();
    }

    /**
     * Provisions everything both sides need. Idempotent, like the script.
     */
    void provision() {

        final String admin = adminToken();

        createClient(admin, Map.of(
                "clientId", CLI_CLIENT,
                "enabled", true,
                "publicClient", true,
                "standardFlowEnabled", false,
                "directAccessGrantsEnabled", true,
                "protocolMappers", List.of(audienceMapper())));

        createClient(admin, Map.of(
                "clientId", SVC_CLIENT,
                "enabled", true,
                "publicClient", false,
                "serviceAccountsEnabled", true,
                "standardFlowEnabled", false,
                "directAccessGrantsEnabled", false,
                "protocolMappers", List.of(audienceMapper())));

        // The administrator gets tenant-admin, the service account gets svc-tenant-read, and neither is
        // granted directly - each goes through a group carrying exactly its role.
        grantThroughGroup(admin, TENANT_ADMIN, "tenant-admins", userId(admin, adminUser));
        grantThroughGroup(admin, SVC_TENANT_READ, "svc-tenant-readers", serviceAccountUserId(admin, SVC_CLIENT));

        svcClientSecret = clientSecret(admin, SVC_CLIENT);
    }

    /**
     * Returns the secret of the administered application's service account.
     *
     * @return Client secret, available once {@link #provision()} has run.
     */
    String serviceAccountSecret() {
        return Objects.requireNonNull(svcClientSecret, "provision() has not run");
    }

    /**
     * Returns a fresh token of the signed-in administrator.
     * <p>
     * Fetched per call on purpose: Keycloak grants the rights over a realm per realm, so a token minted
     * before {@code registerTenant} created one carries none over it.
     *
     * @return Access token carrying {@code tenant-admin} and the {@code jtenman-api} audience.
     */
    String administratorToken() {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", CLI_CLIENT);
        form.add("username", adminUser);
        form.add("password", adminPassword);
        form.add("grant_type", "password");
        return accessToken(form);
    }

    /**
     * Returns a token of the service account, as the starter would obtain one.
     *
     * @return Access token carrying {@code svc-tenant-read} and nothing else of interest.
     */
    String serviceAccountToken() {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", SVC_CLIENT);
        form.add("client_secret", serviceAccountSecret());
        form.add("grant_type", "client_credentials");
        return accessToken(form);
    }

    /**
     * Returns the token endpoint of the administration realm.
     *
     * @return Token URI.
     */
    String tokenUri() {
        return baseUrl + "/realms/" + ADMIN_REALM + "/protocol/openid-connect/token";
    }

    /**
     * Returns the issuer of the administration realm - the one realm jtenman accepts.
     *
     * @return Issuer URI.
     */
    String issuerUri() {
        return baseUrl + "/realms/" + ADMIN_REALM;
    }

    private static Map<String, Object> audienceMapper() {
        return Map.of(
                "name", "audience",
                "protocol", "openid-connect",
                "protocolMapper", "oidc-audience-mapper",
                "config", Map.of(
                        "included.custom.audience", AUDIENCE,
                        "access.token.claim", "true",
                        "id.token.claim", "false"));
    }

    private void createClient(final String admin, final Map<String, Object> representation) {
        if (!findClients(admin, (String) representation.get("clientId")).isEmpty()) {
            return;
        }
        rest.post().uri(adminUri("/clients"))
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(representation)
                .retrieve().toBodilessEntity();
    }

    private void grantThroughGroup(final String admin, final String role, final String group,
            final String userId) {

        if (roleByName(admin, role) == null) {
            rest.post().uri(adminUri("/roles"))
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", role))
                    .retrieve().toBodilessEntity();
        }
        final Map<String, Object> roleRepresentation = roleByName(admin, role);

        String groupId = groupId(admin, group);
        if (groupId == null) {
            rest.post().uri(adminUri("/groups"))
                    .header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", group))
                    .retrieve().toBodilessEntity();
            groupId = Objects.requireNonNull(groupId(admin, group), "group '" + group + "' was not created");
        }

        rest.post().uri(adminUri("/groups/" + groupId + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", roleRepresentation.get("id"), "name", role)))
                .retrieve().toBodilessEntity();

        rest.put().uri(adminUri("/users/" + userId + "/groups/" + groupId))
                .header("Authorization", "Bearer " + admin)
                .retrieve().toBodilessEntity();
    }

    private String accessToken(final MultiValueMap<String, String> form) {
        final Map<?, ?> body = rest.post().uri(tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(Map.class);
        final Object token = Objects.requireNonNull(body, "no token response").get("access_token");
        return Objects.requireNonNull((String) token, "no access_token in " + body);
    }

    private String adminToken() {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", "admin-cli");
        form.add("username", adminUser);
        form.add("password", adminPassword);
        form.add("grant_type", "password");
        return accessToken(form);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findClients(final String admin, final String clientId) {
        return rest.get().uri(adminUri("/clients?clientId=" + clientId))
                .header("Authorization", "Bearer " + admin)
                .retrieve().body(List.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> roleByName(final String admin, final String role) {
        try {
            return rest.get().uri(adminUri("/roles/" + role))
                    .header("Authorization", "Bearer " + admin)
                    .retrieve().body(Map.class);
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String groupId(final String admin, final String group) {
        final List<Map<String, Object>> groups = rest.get()
                .uri(adminUri("/groups?search=" + group + "&exact=true"))
                .header("Authorization", "Bearer " + admin)
                .retrieve().body(List.class);
        if (groups == null) {
            return null;
        }
        return groups.stream()
                .filter(g -> group.equals(g.get("name")))
                .map(g -> (String) g.get("id"))
                .findFirst().orElse(null);
    }

    private String userId(final String admin, final String username) {
        final List<Map<String, Object>> users = rest.get()
                .uri(adminUri("/users?username=" + username + "&exact=true"))
                .header("Authorization", "Bearer " + admin)
                .retrieve().body(List.class);
        Objects.requireNonNull(users, "no users returned");
        return (String) users.getFirst().get("id");
    }

    @SuppressWarnings("unchecked")
    private String serviceAccountUserId(final String admin, final String clientId) {
        final String uuid = (String) findClients(admin, clientId).getFirst().get("id");
        final Map<String, Object> user = rest.get()
                .uri(adminUri("/clients/" + uuid + "/service-account-user"))
                .header("Authorization", "Bearer " + admin)
                .retrieve().body(Map.class);
        return (String) Objects.requireNonNull(user, "no service account user").get("id");
    }

    @SuppressWarnings("unchecked")
    private String clientSecret(final String admin, final String clientId) {
        final String uuid = (String) findClients(admin, clientId).getFirst().get("id");
        final Map<String, Object> secret = rest.get()
                .uri(adminUri("/clients/" + uuid + "/client-secret"))
                .header("Authorization", "Bearer " + admin)
                .retrieve().body(Map.class);
        return (String) Objects.requireNonNull(secret, "no client secret").get("value");
    }

    private String adminUri(final String path) {
        return baseUrl + "/admin/realms/" + ADMIN_REALM + path;
    }

}

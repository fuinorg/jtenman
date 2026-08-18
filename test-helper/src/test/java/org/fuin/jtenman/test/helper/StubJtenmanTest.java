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
package org.fuin.jtenman.test.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the stub itself - what it serves and, above all, in what shape.
 * <p>
 * A stub that quietly stops resembling the real thing is worse than no stub: every test using it goes on
 * passing while the code it exercises has stopped working against jtenman. The shape assertions below
 * are checked against a <b>capture</b> of a real answer rather than against what this stub happens to
 * produce - see {@link #CAPTURED}.
 */
class StubJtenmanTest {

    private static final String APPLICATION = "billing";

    private static final String REALM = "acme";

    private static final URI ISSUER = URI.create("http://localhost:8180/realms/acme");

    /**
     * A real answer from a running {@code jtenman-combined} with one registered and subscribed tenant:
     *
     * <pre>
     * curl -s -H "Authorization: Bearer $TOKEN" \
     *   'http://localhost:9090/view/tenant/list-by-application?application=melkheftken'
     * </pre>
     * <p>
     * This is the contract. Value objects are single-field wrappers, so whether a realm arrives as
     * {@code "acme"} or as {@code {"value":"acme","baseType":"java.lang.String"}} decides whether a
     * consumer's pull works at all - and that difference is one Jackson module away in either direction.
     */
    private static final String CAPTURED = """
            [{"source":{"entityIdPath":"TENANT acme","aggregateVersion":0},\
            "realm":"acme",\
            "issuerUri":"http://localhost:8180/realms/acme",\
            "status":"ACTIVE"}]""";

    private static final ObjectMapper JSON = new ObjectMapper();

    private StubJtenman testee;

    @BeforeEach
    void setUp() {
        testee = StubJtenman.start();
    }

    @AfterEach
    void tearDown() {
        testee.close();
    }

    /**
     * The one that matters: what the stub writes has the same shape as what jtenman writes.
     * <p>
     * Everything but the aggregate version, which is a projection detail no consumer reads and which
     * this stub counts for itself.
     */
    @Test
    void testItServesTheSameShapeAsJtenman() throws Exception {

        // GIVEN
        testee.subscribe(APPLICATION, REALM, ISSUER);

        // WHEN
        final JsonNode served = JSON.readTree(list(APPLICATION).body());
        final JsonNode captured = JSON.readTree(CAPTURED);

        // THEN
        assertThat(served).hasSize(1);
        final JsonNode servedRow = served.get(0);
        final JsonNode capturedRow = captured.get(0);

        assertThat(fieldNames(servedRow)).isEqualTo(fieldNames(capturedRow));
        assertThat(fieldNames(servedRow.get("source"))).isEqualTo(fieldNames(capturedRow.get("source")));
        assertThat(servedRow.get("realm")).isEqualTo(capturedRow.get("realm"));
        assertThat(servedRow.get("issuerUri")).isEqualTo(capturedRow.get("issuerUri"));
        assertThat(servedRow.get("status")).isEqualTo(capturedRow.get("status"));
        assertThat(servedRow.get("source").get("entityIdPath"))
                .isEqualTo(capturedRow.get("source").get("entityIdPath"));
        assertThat(servedRow.get("source").get("aggregateVersion").isNumber()).isTrue();

    }

    @Test
    void testTheListIsPerApplication() {

        // GIVEN
        testee.subscribe(APPLICATION, REALM, ISSUER);

        // WHEN & THEN
        assertThat(list(APPLICATION).body()).contains("\"acme\"");
        assertThat(list("somebody-else").body()).isEqualTo("[]");
        assertThat(list("").body()).isEqualTo("[]");

    }

    @Test
    void testATenantCanBeAddedAndRevoked() {

        // GIVEN
        testee.subscribe(APPLICATION, REALM, ISSUER);
        testee.subscribe(APPLICATION, "globex", URI.create("http://localhost:8180/realms/globex"));
        assertThat(list(APPLICATION).body()).contains("acme").contains("globex");

        // WHEN
        testee.unsubscribe(APPLICATION, "globex");

        // THEN
        assertThat(list(APPLICATION).body()).contains("acme").doesNotContain("globex");

        testee.unsubscribeAll();
        assertThat(list(APPLICATION).body()).isEqualTo("[]");

    }

    /** Subscribing the same realm twice replaces it rather than listing it twice. */
    @Test
    void testResubscribingReplaces() throws Exception {

        // GIVEN
        testee.subscribe(APPLICATION, REALM, ISSUER);

        // WHEN
        testee.subscribe(APPLICATION, REALM, URI.create("http://elsewhere/realms/acme"));

        // THEN
        final JsonNode served = JSON.readTree(list(APPLICATION).body());
        assertThat(served).hasSize(1);
        assertThat(served.get(0).get("issuerUri").asText()).isEqualTo("http://elsewhere/realms/acme");

    }

    /**
     * The real jtenman never serves a suspended tenant. Being able to is what lets a consumer prove its
     * own filter works rather than assuming the control plane will always do it.
     */
    @Test
    void testASuspendedTenantCanBeServedOnPurpose() {

        // GIVEN
        testee.subscribe(APPLICATION, REALM, ISSUER, TenantStatus.SUSPENDED);

        // WHEN & THEN
        assertThat(list(APPLICATION).body()).contains("SUSPENDED");

    }

    @Test
    void testAnOutageCanBeSimulated() {

        // GIVEN
        testee.subscribe(APPLICATION, REALM, ISSUER);

        // WHEN
        testee.answerWith(503);

        // THEN
        assertThat(list(APPLICATION).statusCode()).isEqualTo(503);
        testee.recover();
        assertThat(list(APPLICATION).statusCode()).isEqualTo(200);

    }

    @Test
    void testTheTokenCanBeMadeMandatory() {

        // GIVEN
        testee.subscribe(APPLICATION, REALM, ISSUER);
        assertThat(list(APPLICATION).statusCode()).isEqualTo(200);

        // WHEN
        testee.requireBearerToken();

        // THEN
        assertThat(list(APPLICATION).statusCode()).isEqualTo(401);
        assertThat(list(APPLICATION, "Bearer the-token").statusCode()).isEqualTo(200);

    }

    @Test
    void testAnyOtherPathIsNotFound() {

        // WHEN & THEN
        assertThat(get(URI.create(testee.url() + "/view/tenant/whatever")).statusCode()).isEqualTo(404);

    }

    @Test
    void testItRecordsWhatItWasAsked() {

        // GIVEN
        list(APPLICATION, "Bearer the-token");

        // WHEN & THEN
        assertThat(testee.pulls()).isEqualTo(1);
        assertThat(testee.requests()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo(StubJtenman.PATH);
            assertThat(request.application()).contains(APPLICATION);
            assertThat(request.authorization()).contains("Bearer the-token");
        });

        testee.forgetRequests();
        assertThat(testee.pulls()).isZero();

    }

    private HttpResponse<String> list(final String application) {
        return list(application, null);
    }

    private HttpResponse<String> list(final String application, final String authorization) {
        final URI uri = URI.create(testee.url() + StubJtenman.PATH + "?"
                + StubJtenman.APPLICATION_PARAMETER + "=" + application);
        return get(uri, authorization);
    }

    private static HttpResponse<String> get(final URI uri) {
        return get(uri, null);
    }

    private static HttpResponse<String> get(final URI uri, final String authorization) {
        final HttpClient client = HttpClient.newHttpClient();
        try {
            final HttpRequest.Builder request = HttpRequest.newBuilder(uri).GET();
            if (authorization != null) {
                request.header("Authorization", authorization);
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (final IOException ex) {
            throw new UncheckedIOException("Could not call " + uri, ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static List<String> fieldNames(final JsonNode node) {
        final List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        java.util.Collections.sort(names);
        return names;
    }

}

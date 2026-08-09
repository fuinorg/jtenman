package org.fuin.jtenman.test.helper;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.fuin.ddd4j.core.EntityIdPath;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.fuin.ddd4j.jackson.Ddd4JacksonModule;
import org.fuin.ddd4j.jackson.JandexJacksonModule;
import org.fuin.dsl.cqrs.common.basics.VersionedEntityIdPath;
import org.fuin.jtenman.shared.JtenmanJacksonModule;
import org.fuin.jtenman.shared.domain.tenants.IssuerUri;
import org.fuin.jtenman.shared.domain.tenants.RealmName;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.fuin.jtenman.shared.domain.tenants.TenantRealmId;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.fuin.objects4j.common.ThreadSafe;
import org.fuin.objects4j.jackson.Objects4JJacksonModule;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * jtenman's tenant-list endpoint, without jtenman.
 * <p>
 * An application that adds {@code jtenman-starter} has moved the answer to "whose tokens do I accept"
 * out of its own process. Testing what it then does - a tenant appearing, a tenant being revoked, the
 * control plane going away - would otherwise mean starting jtenman, its event store, its database and a
 * Keycloak, for behaviour that is entirely about one HTTP response. This serves that response.
 *
 * <h2>What it is for, and what it is not</h2>
 * <p>
 * It answers {@code GET /view/tenant/list-by-application}, which is the only endpoint the starter calls.
 * Point {@code jtenman.registry.url} at {@link #url()} and the starter cannot tell the difference.
 * <p>
 * It is <b>not</b> a jtenman: nothing here registers a tenant, invites an administrator or touches
 * Keycloak. Those are jtenman's own concern and are tested in jtenman. What a consuming application
 * needs to know is narrower - that its replica follows the list, and that it fails closed when the list
 * cannot be fetched - and both are reachable from here.
 *
 * <h2>The list is built from real types</h2>
 * <p>
 * The rows are {@link TenantDetails} objects serialized with the same Jackson configuration the starter
 * reads them with, rather than hand-written JSON. Hand-written JSON is the usual way a stub like this
 * rots: the value objects are single-field wrappers, so the difference between {@code "acme"} and
 * {@code {"value":"acme"}} decides whether the pull works at all, and a stub carrying its own copy of
 * that decision would go on passing while the real thing broke.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * try (StubJtenman jtenman = StubJtenman.start()) {
 *     jtenman.subscribe("billing", "acme", URI.create("https://keycloak/realms/acme"));
 *     // ... start the application with jtenman.registry.url = jtenman.url()
 *     jtenman.unsubscribe("billing", "acme");     // revocation, seen within one refresh interval
 *     jtenman.answerWith(503);                    // outage, until the staleness bound is exceeded
 * }
 * </pre>
 */
@ThreadSafe
public final class StubJtenman implements AutoCloseable {

    /**
     * Path the starter's generated client calls.
     * <p>
     * A literal rather than a reference to {@code TenantControllerApi}, so this module does not force
     * {@code spring-web} onto a consumer that uses the Quarkus flavour of the same contract.
     * {@code StubJtenmanContractTest} reads the annotations off the generated interface and fails if
     * this stops matching them.
     */
    public static final String PATH = "/view/tenant/list-by-application";

    /** Query parameter naming the application whose tenants are wanted. */
    public static final String APPLICATION_PARAMETER = "application";

    /**
     * One request the stub received.
     *
     * @param method HTTP method.
     * @param path Request path.
     * @param application Value of the {@code application} parameter, or empty when it was missing.
     * @param authorization Value of the {@code Authorization} header, or empty when none was sent.
     */
    public record Recorded(String method, String path, Optional<String> application,
            Optional<String> authorization) {
    }

    private final HttpServer server;

    private final ObjectMapper mapper = tenantListObjectMapper();

    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());

    /** Application id to its tenants, keyed by realm so a re-subscription replaces rather than doubles. */
    private final Map<String, Map<String, TenantDetails>> subscriptions = new LinkedHashMap<>();

    private final AtomicInteger version = new AtomicInteger();

    private volatile int status = 200;

    private volatile boolean bearerRequired;

    private StubJtenman(final HttpServer server) {
        this.server = server;
    }

    /**
     * Starts the stub on a loopback port the operating system picks.
     *
     * @return Running stub. Close it when the test is done.
     */
    public static StubJtenman start() {
        try {
            final HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            final StubJtenman stub = new StubJtenman(server);
            server.createContext("/", stub::handle);
            server.start();
            return stub;
        } catch (final IOException ex) {
            throw new UncheckedIOException("Failed to start the stub jtenman", ex);
        }
    }

    /**
     * Returns the base URL to configure {@code jtenman.registry.url} with.
     *
     * @return Base URL, without a trailing slash.
     */
    public URI url() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /**
     * Adds an active tenant to one application's list, or replaces it if the realm is already there.
     *
     * @param application Application id, as in {@code jtenman.registry.application}.
     * @param realm Realm name, which is also the tenant id the starter derives from it.
     * @param issuerUri Issuer URI the tenant's tokens carry.
     */
    public void subscribe(final String application, final String realm, final URI issuerUri) {
        subscribe(application, realm, issuerUri, TenantStatus.ACTIVE);
    }

    /**
     * Adds a tenant with an explicit status.
     * <p>
     * The real jtenman leaves suspended tenants out of the answer entirely, so serving one is not
     * something it does. It is here so a consuming application can prove its <em>own</em> filter works:
     * {@code JtenmanTenantRepository} filters again on purpose, and an application that reimplements the
     * pull has no reason to assume the control plane will always do the filtering for it.
     *
     * @param application Application id.
     * @param realm Realm name.
     * @param issuerUri Issuer URI the tenant's tokens carry.
     * @param status Status to report.
     */
    public void subscribe(final String application, final String realm, final URI issuerUri,
            final TenantStatus status) {
        final TenantDetails details = new TenantDetails(
                new VersionedEntityIdPath(new EntityIdPath(new TenantRealmId(realm)),
                        version.incrementAndGet()),
                new RealmName(realm), new IssuerUri(issuerUri.toString()), status);
        synchronized (subscriptions) {
            subscriptions.computeIfAbsent(application, key -> new LinkedHashMap<>()).put(realm, details);
        }
    }

    /**
     * Removes a tenant from one application's list - the revocation a consuming application must notice
     * within one refresh interval.
     *
     * @param application Application id.
     * @param realm Realm name.
     */
    public void unsubscribe(final String application, final String realm) {
        synchronized (subscriptions) {
            final Map<String, TenantDetails> tenants = subscriptions.get(application);
            if (tenants != null) {
                tenants.remove(realm);
            }
        }
    }

    /** Empties every list, as a jtenman that lost its projection would answer. */
    public void unsubscribeAll() {
        synchronized (subscriptions) {
            subscriptions.clear();
        }
    }

    /**
     * Makes every request fail with a status code, so the consumer's staleness bound can be reached.
     *
     * @param statusCode Status to answer with, e.g. {@code 503}.
     */
    public void answerWith(final int statusCode) {
        status = statusCode;
    }

    /** Answers normally again after {@link #answerWith(int)}. */
    public void recover() {
        status = 200;
    }

    /**
     * Rejects a request that carries no bearer token, as the real jtenman does.
     * <p>
     * Off by default, so a test about replication does not have to arrange a service account. Switch it
     * on for the one test that checks the consumer actually sends its token - which is worth having,
     * because an unauthenticated pull fails in exactly the same way as an unreachable control plane.
     */
    public void requireBearerToken() {
        bearerRequired = true;
    }

    /**
     * Returns every request received, in order.
     *
     * @return Recorded requests.
     */
    public List<Recorded> requests() {
        return List.copyOf(requests);
    }

    /**
     * Returns how many times the list has been fetched - a refresher that stopped refreshing is the
     * failure this counts.
     *
     * @return Number of requests received.
     */
    public int pulls() {
        return requests.size();
    }

    /** Forgets the requests received so far, so a second phase can be asserted on its own. */
    public void forgetRequests() {
        requests.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            final Map<String, String> parameters = parse(exchange.getRequestURI().getRawQuery());
            final Optional<String> application = Optional.ofNullable(
                    parameters.get(APPLICATION_PARAMETER));
            final Optional<String> authorization = Optional.ofNullable(
                    exchange.getRequestHeaders().getFirst("Authorization"));
            final String path = exchange.getRequestURI().getPath();
            requests.add(new Recorded(exchange.getRequestMethod(), path, application, authorization));

            if (!PATH.equals(path)) {
                respond(exchange, 404, "{}");
                return;
            }
            if (bearerRequired && authorization.filter(value -> value.startsWith("Bearer ")).isEmpty()) {
                respond(exchange, 401, "{}");
                return;
            }
            final int current = status;
            if (current != 200) {
                respond(exchange, current, "{}");
                return;
            }
            respond(exchange, 200, listOf(application.orElse("")));
        }
    }

    private String listOf(final String application) {
        final List<TenantDetails> tenants;
        synchronized (subscriptions) {
            tenants = List.copyOf(subscriptions.getOrDefault(application, Map.of()).values());
        }
        try {
            return mapper.writeValueAsString(tenants);
        } catch (final JsonProcessingException ex) {
            throw new IllegalStateException("Could not write the tenant list", ex);
        }
    }

    /**
     * The mapper, configured exactly as the starter's reading side is.
     *
     * @return Mapper that writes what {@code TenantRegistryAutoConfiguration} can read.
     */
    private static ObjectMapper tenantListObjectMapper() {
        return new ObjectMapper()
                // The value objects are single-field wrappers with no getters.
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .registerModules(List.of(
                        new JtenmanJacksonModule(),
                        new Objects4JJacksonModule(),
                        new Ddd4JacksonModule(new JandexEntityIdFactory()),
                        new JandexJacksonModule("org.fuin.jtenman")));
    }

    private static Map<String, String> parse(final String rawQuery) {
        final Map<String, String> parameters = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return parameters;
        }
        for (final String pair : rawQuery.split("&")) {
            final int equals = pair.indexOf('=');
            if (equals > 0) {
                parameters.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
            }
        }
        return parameters;
    }

    private static String decode(final String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void respond(final HttpExchange exchange, final int statusCode, final String json)
            throws IOException {
        final byte[] out = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, out.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(out);
        }
    }

}

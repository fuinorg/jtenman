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
package org.fuin.jtenman.starter;

import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenant;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.ddd4j.core.TenantAddedEvent;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.TenantRemovedEvent;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantService;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.jtenman.shared.domain.tenants.TenantDetails;
import org.fuin.jtenman.shared.domain.tenants.TenantStatus;
import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * The tenant repository of an application administered by jtenman: a replica of the list jtenman keeps,
 * refreshed by {@link TenantRegistryRefresher}.
 * <p>
 * It replaces the {@code KeycloakTenantRepository} the Keycloak starter would otherwise auto-configure.
 * That one discovers realms <b>on demand</b> and accepts every one of them, which is no admission
 * control at all and no revocation ever - a realm it once accepted stays accepted until the application
 * restarts. Here the answer to "is this realm a tenant of this application" comes from the control
 * plane, and it can change while the application runs.
 *
 * <h2>Two maps, and why</h2>
 *
 * <ul>
 * <li>The <b>admission list</b> - issuer URI to {@link TenantId} - is what jtenman said, and needs no
 *     network call of its own beyond the pull. It drives {@link #getTenantIds()} and the added/removed
 *     events.</li>
 * <li>The <b>verification material</b> - issuer URI to {@link JwtTenant} - is the OpenID Connect
 *     configuration of that realm, which has to be fetched from Keycloak. It drives
 *     {@link #findByIssuer(String)}.</li>
 * </ul>
 * <p>
 * Both are filled on the refresh thread, and that is the point: <b>no request thread ever performs
 * discovery here.</b> The realm-discovering repository could not do this - it learns of an issuer only
 * when a token carrying it arrives - and pays for it with a negative cache to stop a slow Keycloak from
 * occupying every request thread that mentions the same issuer. An explicit list removes the problem
 * rather than mitigating it: an issuer that is not on the list is rejected by a map lookup, so no
 * unknown issuer can cause any I/O at all.
 * <p>
 * A tenant whose discovery failed stays on the admission list and is retried on the next pull. Until it
 * succeeds its tokens are rejected - the application knows the realm is a tenant but has no keys to
 * check its signatures with, and guessing is not an option.
 *
 * <h2>Fail closed, and a bound on how long</h2>
 * <p>
 * Before the first successful pull the repository accepts nobody. That is deliberate: an application
 * that cannot ask which realms are its tenants must not fall back to trusting any.
 * <p>
 * After that a list that <em>stops</em> refreshing is trusted only for
 * {@code jtenman.registry.max-staleness}, then the repository accepts nobody again. A replicated
 * revocation list that can no longer be verified is exactly as good as its age, and the alternative -
 * serving a snapshot of unbounded age - means a suspended tenant keeps working for as long as the
 * outage lasts. Set the property to zero to trade that guarantee for availability; the choice is real
 * and belongs to the operator, not to this class.
 *
 * <h2>Where the realm name has to line up</h2>
 * <p>
 * A resource server has one way of knowing which tenant a request belongs to: <b>the last path segment
 * of the token's issuer</b>. That makes two rows in jtenman's catalogue inadmissible, and both are
 * dropped here rather than left to be discovered later:
 * <ul>
 * <li>A realm name that is <b>not</b> its issuer's last segment. The list would then name the tenant one
 *     way and its tokens another - one tenant split in two, with the data going to whichever half the
 *     writer happened to use.</li>
 * <li>One realm name registered for <b>two issuers</b>. They are separate organisations verified against
 *     separate key sets, and they would land on one set of streams, one schema and one set of
 *     checkpoints - two tenants merged into one, reported by nothing.</li>
 * </ul>
 * <p>
 * Neither is a forgery route - a caller still has to hold a token signed by a realm on this list - which
 * is exactly why they are worth refusing here: every request involved is legitimate, so nothing further
 * down is ever going to object.
 */
@ThreadSafe
public class JtenmanTenantRepository implements JwtTenantRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JtenmanTenantRepository.class);

    private final TenantService tenantService;

    private final ApplicationId application;

    private final Duration maxStaleness;

    private final ApplicationEventPublisher publisher;

    /** What jtenman last said. Replaced wholesale, never mutated. */
    private volatile Map<String, TenantId> admitted = Map.of();

    /** OIDC configuration per issuer, resolved on the refresh thread. */
    private final Map<String, JwtTenant> verification = new ConcurrentHashMap<>();

    /** Epoch milliseconds of the last successful pull, {@code 0} while there has been none. */
    private volatile long lastSuccess;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param tenantService Read model of jtenman, normally reached over HTTP.
     * @param application Identifier of this application in jtenman's catalogue - the list is per
     *                    application, so this decides which tenants are replicated.
     * @param maxStaleness How long a list that cannot be refreshed keeps being trusted. Zero switches
     *                     the bound off.
     * @param publisher Used to announce tenants that appeared and disappeared.
     */
    public JtenmanTenantRepository(final TenantService tenantService, final ApplicationId application,
            final Duration maxStaleness, final ApplicationEventPublisher publisher) {
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService==null");
        this.application = Objects.requireNonNull(application, "application==null");
        this.maxStaleness = Objects.requireNonNull(maxStaleness, "maxStaleness==null");
        if (maxStaleness.isNegative()) {
            throw new IllegalArgumentException("maxStaleness must not be negative, but was: " + maxStaleness);
        }
        this.publisher = Objects.requireNonNull(publisher, "publisher==null");
    }

    /**
     * Fetches the tenant list, announces what changed and resolves the verification material of tenants
     * that do not have it yet.
     * <p>
     * Called by {@link TenantRegistryRefresher}; safe to call at any time. A failure to reach jtenman
     * propagates and leaves the previous list in place - it says nothing about which realms are tenants,
     * only that this application could not ask. The staleness bound is what limits how long that lasts.
     *
     * @throws RuntimeException The list could not be fetched.
     */
    public void refresh() {

        final Map<String, TenantId> current = admissible(fetch());

        final Map<String, TenantId> previous = admitted;
        admitted = Map.copyOf(current);
        lastSuccess = now();

        announce(previous, current);
        resolveMissing(current);
    }

    @Override
    public Optional<JwtTenant> findByIssuer(final String issuerUri) {
        Objects.requireNonNull(issuerUri, "issuerUri==null");
        if (!usable()) {
            return Optional.empty();
        }
        if (!admitted.containsKey(issuerUri)) {
            // Not "unknown tenant, go and discover it" - this application serves the realms jtenman
            // named and no others.
            return Optional.empty();
        }
        return Optional.ofNullable(verification.get(issuerUri));
    }

    @Override
    public Stream<TenantId> getTenantIds() {
        if (!usable()) {
            return Stream.empty();
        }
        return admitted.values().stream();
    }

    /**
     * Returns whether the replica may currently be used - there has been a successful pull and it is not
     * older than the configured bound.
     *
     * @return {@literal true} if the list is being served, {@literal false} if the repository is
     *         accepting nobody.
     */
    public boolean usable() {
        final long last = lastSuccess;
        if (last == 0) {
            return false;
        }
        return maxStaleness.isZero() || now() - last <= maxStaleness.toMillis();
    }

    /**
     * Returns when the list was last fetched successfully.
     *
     * @return Point in time, or empty if there has been no successful pull yet.
     */
    public Optional<Instant> lastSuccessfulRefresh() {
        final long last = lastSuccess;
        return last == 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(last));
    }

    /**
     * Fetches the list. Overridable for testing.
     *
     * @return Active tenants of this application.
     */
    protected List<TenantDetails> fetch() {
        return tenantService.listByApplication(application);
    }

    /**
     * Resolves the OpenID Connect configuration of one realm. Overridable for testing.
     *
     * @param issuerUri Issuer URI of the tenant.
     *
     * @return Resolved tenant.
     */
    protected JwtTenant resolve(final String issuerUri) {
        return new JwtTenant(issuerUri);
    }

    /**
     * Returns the current time in epoch milliseconds. Overridable for testing.
     *
     * @return Current time.
     */
    protected long now() {
        return System.currentTimeMillis();
    }

    /**
     * Reduces jtenman's answer to the entries this application may act on.
     * <p>
     * Entries are dropped rather than the pull being failed. A failed {@link #refresh()} means "could not
     * ask", which leaves the previous list standing and eventually stops the application from serving
     * anybody; that is the right answer to an unreachable jtenman and the wrong one to a list that
     * arrived and had a bad row in it. The other tenants in that list are answerable and keep working,
     * and the entry that cannot be acted on is simply not admitted - which is also what already happens
     * to a suspended tenant.
     *
     * @param details What jtenman said.
     *
     * @return Issuer URI to tenant identifier, for the entries that survived.
     */
    private Map<String, TenantId> admissible(final List<TenantDetails> details) {

        final Map<String, TenantId> candidates = new LinkedHashMap<>();
        for (final TenantDetails tenant : details) {
            // jtenman leaves suspended tenants out of the answer. Filtering again costs nothing and
            // means a change on that side cannot silently widen what this application accepts.
            if (tenant.getStatus() != TenantStatus.ACTIVE) {
                continue;
            }
            final String issuerUri = tenant.getIssuerUri().asBaseType();
            final String realm = tenant.getRealm().asBaseType();
            if (!realm.equals(realmSegmentOf(issuerUri))) {
                // One tenant, two identifiers: this list would set up schemas, datasources and
                // per-tenant loops under jtenman's realm name, while a token from that realm arrives as
                // the segment of its issuer. The query side reports the mismatch, the event store does
                // not - streams are written under one name and projected under the other, and the read
                // model simply stays empty.
                LOG.error("Tenant '{}' is not admitted: its issuer '{}' ends in '{}', and a resource "
                        + "server derives the tenant from that segment alone - the two have to be the "
                        + "same. Correct the realm name or the issuer URI in jtenman.",
                        realm, issuerUri, realmSegmentOf(issuerUri));
                continue;
            }
            candidates.put(issuerUri, new TenantId(realm));
        }
        return withoutAmbiguousRealms(candidates);
    }

    /**
     * Drops every entry whose realm name is claimed by more than one issuer.
     * <p>
     * Both are dropped, not one: the two are different issuers verified against different key sets, and
     * there is nothing here that could say which of them was meant. Admitting either would give one
     * organisation the other's streams, schema and checkpoints - and would report nothing, because each
     * token is valid and each request is authorised.
     *
     * @param candidates Entries that survived the per-entry checks.
     *
     * @return The subset whose realm names are unique.
     */
    private Map<String, TenantId> withoutAmbiguousRealms(final Map<String, TenantId> candidates) {

        final Map<TenantId, List<String>> issuersPerRealm = new LinkedHashMap<>();
        candidates.forEach((issuerUri, tenantId) ->
                issuersPerRealm.computeIfAbsent(tenantId, id -> new ArrayList<>()).add(issuerUri));

        final Map<String, TenantId> result = new LinkedHashMap<>();
        issuersPerRealm.forEach((tenantId, issuers) -> {
            if (issuers.size() > 1) {
                LOG.error("Realm '{}' is registered for more than one issuer ({}) - none of them is "
                        + "admitted. A resource server derives the tenant from the realm name alone, so "
                        + "these would share streams, schema and checkpoints. Give them distinct realm "
                        + "names in jtenman.", tenantId.name(), String.join(", ", issuers));
            } else {
                result.put(issuers.get(0), tenantId);
            }
        });
        return result;
    }

    /**
     * Returns what a resource server will read as the tenant: the last path segment of the issuer.
     *
     * @param issuerUri Issuer URI of the tenant.
     *
     * @return Trailing segment, which is the empty string if the URI ends in a slash.
     */
    private static String realmSegmentOf(final String issuerUri) {
        return issuerUri.substring(issuerUri.lastIndexOf('/') + 1);
    }

    private void announce(final Map<String, TenantId> previous, final Map<String, TenantId> current) {
        for (final Map.Entry<String, TenantId> entry : current.entrySet()) {
            if (!previous.containsKey(entry.getKey())) {
                LOG.info("Tenant added to this application: {} ({})", entry.getValue().name(), entry.getKey());
                publisher.publishEvent(new TenantAddedEvent(new RegisteredTenant(entry.getValue())));
            }
        }
        for (final Map.Entry<String, TenantId> entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                // Drop the keys first: the listeners evict their caches on the event, and there is no
                // reason to keep material for a realm this application no longer accepts.
                verification.remove(entry.getKey());
                LOG.info("Tenant removed from this application: {} ({}) - its tokens are rejected from now on",
                        entry.getValue().name(), entry.getKey());
                publisher.publishEvent(new TenantRemovedEvent(new RegisteredTenant(entry.getValue())));
            }
        }
    }

    private void resolveMissing(final Map<String, TenantId> current) {
        for (final Map.Entry<String, TenantId> entry : current.entrySet()) {
            final String issuerUri = entry.getKey();
            if (verification.containsKey(issuerUri)) {
                continue;
            }
            try {
                verification.put(issuerUri, resolve(issuerUri));
                LOG.info("Resolved the signing keys of tenant '{}' ({})", entry.getValue().name(), issuerUri);
            } catch (final RuntimeException ex) {
                // Keep it on the admission list and try again next time. Its tokens are rejected until
                // then, which is the only honest answer while the keys to check them are unknown.
                LOG.warn("Could not resolve tenant '{}' ({}) - its tokens are rejected until the next "
                        + "refresh succeeds", entry.getValue().name(), issuerUri, ex);
            }
        }
    }

}

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

import org.fuin.objects4j.common.Immutable;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings an administered application needs to replicate its tenant list from jtenman.
 * <p>
 * {@link #getUrl()} and {@link #getApplication()} have <b>no default</b>. An application that adds this
 * starter has decided to take its tenants from jtenman; guessing either value would mean guessing whose
 * tokens it trusts, so a missing one fails the context rather than falling back to something.
 */
@Immutable
@ConfigurationProperties(TenantRegistryProperties.PREFIX)
public class TenantRegistryProperties {

    /** Prefix of all settings of this starter. */
    public static final String PREFIX = "jtenman.registry";

    /** How often the tenant list is fetched again, unless configured otherwise. */
    static final Duration DEFAULT_REFRESH_INTERVAL = Duration.ofSeconds(30);

    /** How long a list that cannot be refreshed keeps being trusted, unless configured otherwise. */
    static final Duration DEFAULT_MAX_STALENESS = Duration.ofMinutes(5);

    /** Bound on the pull, so a hanging jtenman cannot occupy the refresh thread indefinitely. */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final String url;

    private final String application;

    private final Duration refreshInterval;

    private final Duration maxStaleness;

    private final Duration connectTimeout;

    private final Duration readTimeout;

    @Nullable
    private final String clientRegistrationId;

    /**
     * Constructor with all data.
     *
     * @param url Base URL of jtenman, like {@code http://localhost:9090}. Mandatory.
     * @param application Identifier of <b>this</b> application in jtenman's catalogue. Mandatory, and it
     *                    decides which tenants are returned - naming another application's id replicates
     *                    that application's list.
     * @param refreshInterval How often the list is fetched again. This is also the upper bound on how
     *                        long a revoked tenant keeps being accepted, so it is the number
     *                        {@code security.md} means by "within one refresh interval".
     * @param maxStaleness How long a list that cannot be refreshed keeps being trusted. Once it is
     *                     exceeded the repository accepts nobody, which is the correct posture for a
     *                     revocation list that has gone unverifiable - and a deliberate trade of
     *                     availability for it. {@code 0} switches the bound off and trusts the last
     *                     known list indefinitely.
     * @param connectTimeout Connect timeout of the pull.
     * @param readTimeout Read timeout of the pull.
     * @param clientRegistrationId Id of the {@code spring.security.oauth2.client.registration} entry
     *                             describing the service account the list is fetched as. Absent means
     *                             the pull sends no token at all, which jtenman answers with a 401.
     */
    public TenantRegistryProperties(final String url, final String application,
            final Duration refreshInterval, final Duration maxStaleness,
            final Duration connectTimeout, final Duration readTimeout,
            @Nullable final String clientRegistrationId) {
        this.url = url;
        this.application = application;
        this.refreshInterval = refreshInterval == null ? DEFAULT_REFRESH_INTERVAL : refreshInterval;
        this.maxStaleness = maxStaleness == null ? DEFAULT_MAX_STALENESS : maxStaleness;
        this.connectTimeout = connectTimeout == null ? DEFAULT_TIMEOUT : connectTimeout;
        this.readTimeout = readTimeout == null ? DEFAULT_TIMEOUT : readTimeout;
        this.clientRegistrationId = clientRegistrationId;
    }

    /**
     * Returns the base URL of jtenman.
     *
     * @return Base URL, like {@code http://localhost:9090}.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the identifier of this application in jtenman's catalogue.
     *
     * @return Application identifier.
     */
    public String getApplication() {
        return application;
    }

    /**
     * Returns how often the tenant list is fetched again.
     *
     * @return Refresh interval.
     */
    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * Returns how long a list that could not be refreshed keeps being trusted.
     *
     * @return Maximum staleness, zero if the bound is switched off.
     */
    public Duration getMaxStaleness() {
        return maxStaleness;
    }

    /**
     * Returns the client registration describing the service account the tenant list is fetched as.
     *
     * @return Registration id, or {@literal null} if the pull is unauthenticated.
     */
    @Nullable
    public String getClientRegistrationId() {
        return clientRegistrationId;
    }

    /**
     * Returns the connect timeout of the pull.
     *
     * @return Connect timeout.
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Returns the read timeout of the pull.
     *
     * @return Read timeout.
     */
    public Duration getReadTimeout() {
        return readTimeout;
    }

}

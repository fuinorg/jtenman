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
package org.fuin.jtenman.internal;

import org.fuin.objects4j.common.Immutable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the KurrentDB event store that backs the command side. The defaults point
 * to a local insecure instance, which is what the docker-compose setup and the Testcontainers based
 * integration tests provide.
 */
@Immutable
@ConfigurationProperties(EventStoreProperties.PREFIX)
public class EventStoreProperties {

    /** Prefix of all event store properties. */
    public static final String PREFIX = "jtenman.eventstore";

    private final String host;

    private final int port;

    private final boolean tls;

    private final String user;

    private final String password;

    /**
     * Constructor with all data.
     *
     * @param host Host name of the event store.
     * @param port Port the event store listens on.
     * @param tls TRUE if the connection is TLS secured.
     * @param user User name used to authenticate.
     * @param password Password used to authenticate.
     */
    public EventStoreProperties(final String host, final Integer port, final Boolean tls, final String user,
            final String password) {
        this.host = host == null ? "localhost" : host;
        this.port = port == null ? 2113 : port;
        this.tls = tls != null && tls;
        this.user = user == null ? "admin" : user;
        this.password = password == null ? "changeit" : password;
    }

    /**
     * Returns the host name of the event store.
     *
     * @return Host name.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the port the event store listens on.
     *
     * @return Port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Determines if the connection is TLS secured.
     *
     * @return TRUE if TLS is used.
     */
    public boolean isTls() {
        return tls;
    }

    /**
     * Returns the user name used to authenticate.
     *
     * @return User name.
     */
    public String getUser() {
        return user;
    }

    /**
     * Returns the password used to authenticate.
     *
     * @return Password.
     */
    public String getPassword() {
        return password;
    }

}

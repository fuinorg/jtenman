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
package org.fuin.jtenman.query.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boots the query side (the CQRS read responsibility) as a standalone Spring Boot application. This is the side consuming applications poll for the tenants of their application.
 * <p>
 * jtenman is the tenant control plane and has no tenants of its own: it runs with
 * {@code org.fuin.cqrs4j.multitenancy=false} and pins its trust boundary to one realm (see
 * {@code steering/security.md}).
 */
@SpringBootApplication
public class QueryServerApplication {

    private QueryServerApplication() {
    }

    /**
     * Application entry point.
     *
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        SpringApplication.run(QueryServerApplication.class, args);
    }

}

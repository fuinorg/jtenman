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

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.fuin.cqrs4j.test.helper.SecurityArchRules;
import org.junit.jupiter.api.Test;

/**
 * Tests architectural aspects of the query server, and with it everything on its classpath.
 * <p>
 * Both rules come from {@code cqrs-4-java-test-helper} - the predicate they rest on was copied into
 * every deployable of every project, and drifted: this repository still carried a two-clause version
 * that reports a real filter chain as a blanket bypass. The rule itself now lives in one place; only
 * this class stays per module, because ArchUnit scans the classpath of the module it runs in.
 * <p>
 * What they guard: jtenman's chain is {@code @ConditionalOnMissingBean(SecurityFilterChain)}, so any
 * other chain replaces it silently - the application still starts, still validates tokens, still looks
 * configured, and no longer checks a role. The {@code *ApplicationIT} classes rely on exactly that to
 * boot without a Keycloak, which is why the escape hatch exists and why it has to be fenced.
 */
@AnalyzeClasses(packages = "org.fuin.jtenman", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** A permit-all chain may exist only where it cannot be switched on in production. */
    @ArchTest
    static final ArchRule permit_all_is_gated_on_the_local_profile =
            SecurityArchRules.PERMIT_ALL_IS_GATED_ON_THE_LOCAL_PROFILE;

    /**
     * The other half: gating the chain on a profile is worth nothing if the packaged configuration
     * turns that profile on.
     *
     * @throws Exception Reading the configuration failed.
     */
    @Test
    void testTheLocalProfileIsNotActiveByDefault() throws Exception {
        SecurityArchRules.assertLocalProfileNotActive();
    }

}

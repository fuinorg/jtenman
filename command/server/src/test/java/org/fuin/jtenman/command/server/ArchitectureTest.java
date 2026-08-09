package org.fuin.jtenman.command.server;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.fuin.cqrs4j.test.helper.SecurityArchRules;
import org.junit.jupiter.api.Test;

/**
 * Tests architectural aspects of the command server, and with it everything on its classpath.
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

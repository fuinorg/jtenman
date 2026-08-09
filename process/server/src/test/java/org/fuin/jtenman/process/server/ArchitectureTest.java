package org.fuin.jtenman.process.server;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Tests architectural aspects of the process side deployable and everything on its classpath.
 * <p>
 * One rule, guarding the one way the authorization of the control plane can be lost. The chain in
 * {@code ControlPlaneSecurityAutoConfiguration} is {@code @ConditionalOnMissingBean(SecurityFilterChain)},
 * so any other chain replaces it silently - the application still starts, still validates tokens, still
 * looks configured, and no longer checks a role. The four {@code *ApplicationIT} classes rely on exactly
 * that to boot without a Keycloak, which is why the escape hatch exists and why it has to be fenced.
 * <p>
 * Deliberately a copy of {@code combined}'s test rather than a shared test jar: each deployable scans its
 * own classpath, and {@code combined} does not depend on this module, so without a copy here this
 * module's production sources would go unchecked.
 */
@AnalyzeClasses(packages = "org.fuin.jtenman", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Matches a class that opens <b>every</b> request - it calls both {@code anyRequest()} and
     * {@code permitAll()}.
     * <p>
     * Neither half alone is the signal. The production chain calls {@code anyRequest()} too, on its way to
     * {@code authenticated()}, and a legitimate chain may well {@code permitAll()} a single path such as a
     * health endpoint. Only the combination is the blanket bypass.
     * <p>
     * Matched by method name rather than by declaring type on purpose: {@code anyRequest()} is inherited
     * from {@code AbstractRequestMatcherRegistry} and the deprecated {@code authorizeRequests} API uses
     * different classes again, so a type-based match would quietly stop matching after a Spring Security
     * refactor - the worst outcome for a rule like this, because it keeps passing.
     */
    private static final DescribedPredicate<JavaClass> OPEN_EVERY_REQUEST =
            new DescribedPredicate<>("open every request") {
                @Override
                public boolean test(final JavaClass javaClass) {
                    return callsSpringSecurityMethod(javaClass, "anyRequest")
                            && callsSpringSecurityMethod(javaClass, "permitAll");
                }
            };

    private static final ArchCondition<JavaClass> NOT_EXIST_IN_PRODUCTION_SOURCES =
            new ArchCondition<>("not exist outside test sources") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                    events.add(SimpleConditionEvent.violated(item, item.getFullName()
                            + " permits every request. A permit-all filter chain may exist in test sources "
                            + "only: the production chain is @ConditionalOnMissingBean, so this one would "
                            + "replace it and every '/cmd/**' call would run without the 'tenant-admin' role "
                            + "(steering/security.md, 'Access is restricted to a tenant-admin realm role')."));
                }
            };

    /**
     * Unlike the trust-boundary and role rules, this one has no exception. jtenman ships no development
     * profile, so there is nowhere a permit-all chain legitimately belongs outside a test.
     * <p>
     * If a local-development hatch is ever wanted, do not delete this rule: gate the chain on a profile,
     * change the rule to demand that gate rather than to forbid the chain, and add a second check that no
     * packaged {@code application.yml} activates the profile. A gate nothing verifies is how such a chain
     * ships enabled.
     */
    @ArchTest
    static final ArchRule no_production_class_permits_every_request = classes()
            .that(OPEN_EVERY_REQUEST)
            .should(NOT_EXIST_IN_PRODUCTION_SOURCES)
            .allowEmptyShould(true);

    private static boolean callsSpringSecurityMethod(final JavaClass javaClass, final String methodName) {
        return javaClass.getMethodCallsFromSelf().stream()
                .anyMatch(call -> methodName.equals(call.getTarget().getName())
                        && call.getTargetOwner().getPackageName().startsWith("org.springframework.security"));
    }

}

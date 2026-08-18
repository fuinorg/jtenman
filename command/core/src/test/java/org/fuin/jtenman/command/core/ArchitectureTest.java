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
package org.fuin.jtenman.command.core;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.fuin.ddd4j.core.Repository;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Tests architectural aspects of the write side.
 */
@AnalyzeClasses(packagesOf = ArchitectureTest.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * Flags a call to the one-argument {@code add}/{@code update} of a {@link Repository}. Those
     * overloads store the events with no metadata, so the acting user is lost - see
     * {@code org.fuin.cqrs4j.esc.AuditedRepository}.
     */
    private static final ArchCondition<JavaClass> SAVE_THROUGH_AUDITED_REPOSITORY =
            new ArchCondition<>("save aggregates through AuditedRepository, so the acting user is recorded") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                    item.getMethodCallsFromSelf().stream()
                            .filter(call -> "add".equals(call.getTarget().getName())
                                    || "update".equals(call.getTarget().getName()))
                            .filter(call -> call.getTarget().getRawParameterTypes().size() == 1)
                            .filter(call -> call.getTargetOwner().isAssignableTo(Repository.class))
                            .forEach(call -> events.add(SimpleConditionEvent.violated(item,
                                    call.getDescription() + " - calls Repository."
                                            + call.getTarget().getName() + "(aggregate) without metadata, so the "
                                            + "events are stored with no acting user. Use AuditedRepository."
                                            + call.getTarget().getName() + "(repository, aggregate, context) "
                                            + "instead (steering/security.md, 'Who did it is recorded').")));
                }
            };

    /**
     * jtenman's event stream <b>is</b> the provisioning audit trail - who registered a tenant, who
     * subscribed it, who deleted it. Events are immutable, so one stored without the acting user can
     * never gain it later.
     * <p>
     * Enforced here rather than left to review because the failure is invisible: the command succeeds,
     * the tenant is provisioned, and the only symptom is an empty column in an audit nobody reads until
     * they need it.
     */
    @ArchTest
    static final ArchRule every_aggregate_save_records_the_acting_user = classes()
            .that().resideInAPackage("..command.core.handler..")
            .should(SAVE_THROUGH_AUDITED_REPOSITORY)
            .allowEmptyShould(true);

}

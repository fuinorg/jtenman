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
package org.fuin.jtenman.query.core;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Tests architectural aspects of the read side.
 * <p>
 * All of them guard one rule: <b>a REST controller exposes a read model, it does not implement one.</b>
 * The queries live in a {@code «View»ServiceImpl}, which is also what a caller inside the same
 * application depends on - it reaches the read model through the {@code «View»Service} interface rather
 * than through an internal HTTP call.
 * <p>
 * The controllers are generated into {@code src-gen} and hold nothing but delegation, so today these
 * rules pass by construction. They are here for the day someone adds a hand-written controller, or moves
 * one back into {@code src/main/java} and "just quickly" puts a query in it: the result would still
 * work, which is exactly why review would miss it.
 */
@AnalyzeClasses(packagesOf = ArchitectureTest.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /**
     * A JPA row of a read model. Both halves are needed: the name alone would also match Spring's
     * {@code ResponseEntity}, which every controller legitimately depends on.
     */
    private static final DescribedPredicate<JavaClass> READ_MODEL_ROW =
            resideInAPackage("..query.core.view..").and(simpleNameEndingWith("Entity"))
                    .as("read model rows of a view");

    /**
     * Persistence access in a controller is the concrete form the mistake takes: once a controller can
     * reach an {@code EntityManager} it can hold a query, and the service contract stops being the whole
     * read model. Whatever a controller answers must come from the service it delegates to.
     */
    @ArchTest
    static final ArchRule no_rest_controller_touches_persistence = noClasses()
            .that().resideInAPackage("..query.core.view..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
            .because("a controller exposes the read model over HTTP; the queries belong in the "
                    + "«View»ServiceImpl it delegates to")
            .allowEmptyShould(true);

    /**
     * The read transaction belongs around the query, which runs in the service. On the controller it
     * would open a transaction around a method that only forwards - and if the service ever lost its own
     * annotation, the query would silently run outside one.
     */
    @ArchTest
    static final ArchRule no_rest_controller_declares_a_transaction = noClasses()
            .that().resideInAPackage("..query.core.view..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("the transaction belongs on the «View»ServiceImpl, where the entity manager is used")
            .allowEmptyShould(true);

    /**
     * A read model row is an implementation detail of the service that maps it. A controller handling one
     * would mean the mapping to the published DTO had escaped the service - the same leak as a query, one
     * step later.
     */
    @ArchTest
    static final ArchRule no_rest_controller_handles_read_model_rows = noClasses()
            .that().resideInAPackage("..query.core.view..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat(READ_MODEL_ROW)
            .because("rows are mapped to the published DTO inside the service, within its transaction")
            .allowEmptyShould(true);

}

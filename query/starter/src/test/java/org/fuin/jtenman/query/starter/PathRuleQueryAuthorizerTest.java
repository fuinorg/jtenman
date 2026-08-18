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
package org.fuin.jtenman.query.starter;

import org.fuin.cqrs4j.core.ExecutionContext;
import org.fuin.cqrs4j.core.QueryAuthorizer;
import org.fuin.ddd4j.core.TenantId;
import org.fuin.ddd4j.core.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PathRuleQueryAuthorizer}.
 */
public class PathRuleQueryAuthorizerTest {

    @Test
    public void testPermitsAViewMethod() {
        final QueryAuthorizer.Result result =
                new PathRuleQueryAuthorizer().authorized("TenantView.listByApplication", new TestContext());
        assertThat(result.success()).isTrue();
        assertThat(result.permissionId()).isEqualTo("TenantView.listByApplication");
    }

    @Test
    public void testReportsNoHeldPermissionsBecauseJtenmanRecordsNone() {
        // The success comes from the path rule the caller already passed, not from anything they hold.
        // Saying so keeps the log line honest instead of implying a permission model that does not exist.
        assertThat(new PathRuleQueryAuthorizer().authorized("TenantView.listByApplication", new TestContext())
                .heldPermissions()).isEmpty();
    }

    @Test
    public void testPermitsEvenAnUnknownOperation() {
        // Deliberate: this authorizer does not distinguish operations. A future view that should be
        // restricted is a reason to change this class, not something it silently handles.
        assertThat(new PathRuleQueryAuthorizer().authorized("Whatever.method", new TestContext()).success())
                .isTrue();
    }

    private static final class TestContext implements ExecutionContext {

        @Override
        public TenantId getTenantId() {
            return new TenantId("jtenman");
        }

        @Override
        public User getUser() {
            return new User() {
                @Override
                public String getUserId() {
                    return "subject-1";
                }

                @Override
                public String getUserName() {
                    return "Jane";
                }
            };
        }

    }

}

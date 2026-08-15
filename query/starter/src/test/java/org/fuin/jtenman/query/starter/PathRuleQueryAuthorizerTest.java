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

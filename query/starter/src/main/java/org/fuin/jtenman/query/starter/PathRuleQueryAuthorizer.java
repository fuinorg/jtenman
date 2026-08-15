package org.fuin.jtenman.query.starter;

import org.fuin.cqrs4j.core.ExecutionContext;
import org.fuin.cqrs4j.core.QueryAuthorizer;
import org.fuin.objects4j.common.ThreadSafe;

import java.util.Set;

/**
 * Allows every view method, because jtenman authorizes its reads by path rather than by permission.
 * <p>
 * The generated view controllers ask a {@link QueryAuthorizer} before serving anything. That check exists
 * for applications whose read models are guarded per view method, from permissions the application itself
 * records. <b>jtenman is not one of them.</b> It guards {@code /view/**} with a
 * {@code cqrs4j.security.rules} path rule requiring {@code tenant-admin} or {@code svc-tenant-read}, it has
 * exactly one view with exactly one method, and it holds no permissions to check against - it manages
 * tenants, and explicitly does not manage the users inside them.
 * <p>
 * So this permits everything <em>that has already passed the path rule</em>, and it is a decision rather
 * than a gap. Two things follow, and both are the reason this is a named class rather than a lambda in a
 * configuration:
 * <ul>
 * <li>A request that reaches here has already been authenticated and role-checked by the filter chain. This
 * class is not the only thing standing between a caller and the data.</li>
 * <li>If jtenman ever grows a second view, or a view method that not every {@code tenant-admin} should be
 * able to call, this class is what has to change. Finding a permit-all authorizer at that moment should
 * read as "the decision was made here, revisit it" rather than as an oversight.</li>
 * </ul>
 */
@ThreadSafe
public final class PathRuleQueryAuthorizer implements QueryAuthorizer {

    @Override
    public Result authorized(final String permissionId, final ExecutionContext context) {
        // The empty set is honest: the caller holds no permissions, because jtenman records none. The
        // success does not come from what they hold, it comes from the path rule they already passed.
        return new Result(true, permissionId, Set.of());
    }

}

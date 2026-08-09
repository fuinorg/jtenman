package org.fuin.jtenman.starter;

import org.fuin.objects4j.common.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the replicated tenant list up to date by asking {@link JtenmanTenantRepository} to pull it
 * again, on its own thread.
 * <p>
 * Deliberately driven by its own executor rather than by {@code @Scheduled}: a {@code @Scheduled} method
 * in a library does nothing at all in an application that never enabled scheduling, and a revocation
 * mechanism that quietly does not run is worse than none, because it looks configured. The Keycloak
 * starter's own revalidator is built the same way for the same reason.
 * <p>
 * <b>One attempt is made while the context starts</b>, so an application that comes up next to a healthy
 * jtenman serves its first request with a list rather than with an empty one. It is only an attempt: a
 * failure is logged and the context still starts. Refusing to start would tie every administered
 * application's rollout to jtenman being up at that moment, and the application is not dangerous without
 * the list - it accepts nobody until the first pull succeeds.
 */
@ThreadSafe
public class TenantRegistryRefresher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TenantRegistryRefresher.class);

    private final ScheduledExecutorService scheduler;

    /**
     * Constructor performing the first pull and starting the periodic one.
     *
     * @param repository Replica to keep up to date.
     * @param interval Delay between two pulls. This is the upper bound on how long a tenant that
     *                 jtenman dropped keeps being accepted here.
     */
    public TenantRegistryRefresher(final JtenmanTenantRepository repository, final Duration interval) {
        Objects.requireNonNull(repository, "repository==null");
        Objects.requireNonNull(interval, "interval==null");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive, but was: " + interval);
        }

        pull(repository, true);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            final Thread thread = new Thread(runnable, "jtenman-tenant-refresh");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler.scheduleWithFixedDelay(() -> pull(repository, false),
                interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        LOG.info("Replicating the tenant list from jtenman every {} ms", interval.toMillis());
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private static void pull(final JtenmanTenantRepository repository, final boolean initial) {
        try {
            repository.refresh();
        } catch (final RuntimeException ex) {
            // Never let a failure kill the scheduled task - scheduleWithFixedDelay stops on a throw, and
            // stopping is how this silently becomes a snapshot that is never updated again.
            if (initial) {
                LOG.error("Could not fetch the tenant list from jtenman while starting. This application "
                        + "accepts no token until a later attempt succeeds.", ex);
            } else {
                LOG.error("Could not fetch the tenant list from jtenman. The previous one is kept until "
                        + "'{}.max-staleness' is exceeded, then no token is accepted.",
                        TenantRegistryProperties.PREFIX, ex);
            }
        }
    }

}

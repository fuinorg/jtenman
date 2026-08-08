package org.fuin.jtenman.command.starter;

import org.fuin.objects4j.common.Immutable;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Objects;

/**
 * The applications of this system and where Keycloak lives.
 * <p>
 * Static configuration on purpose: which applications exist changes rarely, and a restart of jtenman -
 * and of nothing else - is an acceptable price. Adding a <em>tenant</em> requires no restart anywhere,
 * which is the asymmetry that matters.
 */
@Immutable
@ConfigurationProperties(JtenmanProperties.PREFIX)
public class JtenmanProperties {

    /** Prefix of all jtenman properties. */
    public static final String PREFIX = "jtenman";

    private final String keycloakUrl;

    private final List<Application> applications;

    /**
     * Constructor with all data.
     *
     * @param keycloakUrl Base URL of the Keycloak instance jtenman administers.
     * @param applications Applications of this system.
     */
    public JtenmanProperties(@Nullable final String keycloakUrl, @Nullable final List<Application> applications) {
        this.keycloakUrl = keycloakUrl == null ? "http://localhost:8180" : keycloakUrl;
        this.applications = applications == null ? List.of() : List.copyOf(applications);
    }

    /**
     * Returns the base URL of the Keycloak instance.
     *
     * @return Base URL, never null.
     */
    public String getKeycloakUrl() {
        return keycloakUrl;
    }

    /**
     * Returns the configured applications.
     *
     * @return Applications, possibly empty.
     */
    public List<Application> getApplications() {
        return applications;
    }

    /**
     * One application of the system.
     */
    @Immutable
    public static class Application {

        private final String id;

        @Nullable
        private final String displayName;

        private final String clientId;

        private final String audience;

        /**
         * Constructor with all data.
         *
         * @param id Identifier used in commands and in the tenant list.
         * @param displayName Human readable name.
         * @param clientId Keycloak client created in a subscribing tenant's realm.
         * @param audience Value the audience mapper emits, which the application validates.
         */
        public Application(final String id, @Nullable final String displayName, final String clientId,
                           final String audience) {
            this.id = Objects.requireNonNull(id, "id==null");
            this.displayName = displayName;
            this.clientId = Objects.requireNonNull(clientId, "clientId==null");
            this.audience = Objects.requireNonNull(audience, "audience==null");
        }

        /**
         * Returns the identifier.
         *
         * @return Identifier.
         */
        public String getId() {
            return id;
        }

        /**
         * Returns the human readable name.
         *
         * @return Display name or null.
         */
        @Nullable
        public String getDisplayName() {
            return displayName;
        }

        /**
         * Returns the Keycloak client id.
         *
         * @return Client id.
         */
        public String getClientId() {
            return clientId;
        }

        /**
         * Returns the audience the client's mapper emits.
         *
         * @return Audience.
         */
        public String getAudience() {
            return audience;
        }

    }

}

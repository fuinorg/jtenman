package org.fuin.jtenman.starter;

import org.fuin.objects4j.common.ThreadSafe;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import java.util.Objects;
import java.util.Optional;

/**
 * Fetches the tenant list with a client-credentials token of a service account - in the deployment this
 * starter is written for, the {@code svc-tenant-read} account of this application.
 * <p>
 * The token is obtained through Spring Security's OAuth2 client, so it is cached and renewed before it
 * expires rather than fetched per pull. The registration it names is an ordinary
 * {@code spring.security.oauth2.client.registration.<id>} entry, which is what keeps the client secret
 * out of this project's own configuration surface and in whatever the application already uses to inject
 * one - an environment variable, a mounted file, a secret store.
 *
 * <h2>Not the request-scoped manager</h2>
 * <p>
 * The manager handed in has to be an {@code AuthorizedClientServiceOAuth2AuthorizedClientManager}, or
 * anything else that works without a current request. Spring Boot's auto-configured
 * {@code DefaultOAuth2AuthorizedClientManager} resolves the request and response from the servlet
 * container and throws without them - and every call here happens on the tenant refresh thread, where
 * there is no request at all. {@code TenantListClientCredentialsAutoConfiguration} builds the right one.
 *
 * <h2>Failing to get a token is not "no token"</h2>
 * <p>
 * {@link #bearerToken()} throws rather than returning empty when the authorization server cannot be
 * reached or refuses. Empty means "send no {@code Authorization} header", which is
 * {@link NoOpTenantListAuthProvider}'s deliberate answer; returning it here would turn a failed token
 * request into an unauthenticated call to jtenman, a pointless 401 and a log line naming the wrong
 * problem. The refresher catches it, keeps the previous list and says the pull failed.
 */
@ThreadSafe
public class ClientCredentialsTenantListAuthProvider implements TenantListAuthProvider {

    private final OAuth2AuthorizedClientManager clientManager;

    private final String registrationId;

    /**
     * Constructor with all mandatory dependencies.
     *
     * @param clientManager Manager that obtains and caches the token. Must not need a current request.
     * @param registrationId Id of the {@code spring.security.oauth2.client.registration} entry
     *                       describing the service account.
     */
    public ClientCredentialsTenantListAuthProvider(final OAuth2AuthorizedClientManager clientManager,
            final String registrationId) {
        this.clientManager = Objects.requireNonNull(clientManager, "clientManager==null");
        this.registrationId = Objects.requireNonNull(registrationId, "registrationId==null");
        if (registrationId.isBlank()) {
            throw new IllegalArgumentException("registrationId is empty");
        }
    }

    @Override
    public Optional<String> bearerToken() {
        final OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                // A client-credentials grant has no end user, but the manager insists on a principal to
                // key its cache by. The registration id is the account's identity here.
                .principal(registrationId)
                .build();
        final OAuth2AuthorizedClient client = clientManager.authorize(request);
        if (client == null) {
            throw new IllegalStateException("No token for client registration '" + registrationId
                    + "'. Check that 'spring.security.oauth2.client.registration." + registrationId
                    + "' exists and uses the 'client_credentials' grant.");
        }
        return Optional.of(client.getAccessToken().getTokenValue());
    }

}

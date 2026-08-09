package org.fuin.jtenman.starter;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test for {@link ClientCredentialsTenantListAuthProvider}.
 */
class ClientCredentialsTenantListAuthProviderTest {

    private static final String REGISTRATION_ID = "jtenman";

    @Test
    void testItHandsOutTheTokenOfTheNamedRegistration() {

        final ClientCredentialsTenantListAuthProvider testee =
                new ClientCredentialsTenantListAuthProvider(managerReturning("the-token"), REGISTRATION_ID);

        assertThat(testee.bearerToken()).contains("the-token");

    }

    /**
     * Not being able to get a token is a different thing from having none to send. Returning empty here
     * would call jtenman without an {@code Authorization} header, collect a 401 and report the audience
     * or the role as the problem, when the authorization server was the problem.
     */
    @Test
    void testAMissingAuthorizationIsAFailureRatherThanAnEmptyToken() {

        final ClientCredentialsTenantListAuthProvider testee =
                new ClientCredentialsTenantListAuthProvider(request -> null, REGISTRATION_ID);

        assertThatThrownBy(testee::bearerToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(REGISTRATION_ID)
                .hasMessageContaining("client_credentials");

    }

    /**
     * The manager keys its cache by principal, and a client-credentials grant has no end user to take one
     * from. Passing none makes it throw, so the registration id is used - worth pinning, because the
     * failure would only appear on the first pull.
     */
    @Test
    void testItAuthorizesUnderTheRegistrationIdAsPrincipal() {

        final String[] seen = new String[1];
        final OAuth2AuthorizedClientManager manager = request -> {
            seen[0] = request.getPrincipal().getName();
            return authorizedClient("irrelevant");
        };

        new ClientCredentialsTenantListAuthProvider(manager, REGISTRATION_ID).bearerToken();

        assertThat(seen[0]).isEqualTo(REGISTRATION_ID);

    }

    private static OAuth2AuthorizedClientManager managerReturning(final String token) {
        return request -> authorizedClient(token);
    }

    private static OAuth2AuthorizedClient authorizedClient(final String token) {
        final ClientRegistration registration = ClientRegistration
                .withRegistrationId(REGISTRATION_ID)
                .clientId("billing-svc")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost:8180/realms/master/protocol/openid-connect/token")
                .build();
        final OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                token, Instant.now().minusSeconds(1), Instant.now().plusSeconds(300));
        return new OAuth2AuthorizedClient(registration, REGISTRATION_ID, accessToken);
    }

}

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
package org.fuin.jtenman.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Authenticates the tenant-list pull with a service account.
 * <p>
 * Active as soon as {@code jtenman.registry.client-registration-id} names a
 * {@code spring.security.oauth2.client.registration} entry and Spring Security's OAuth2 client is on the
 * class path. Without it the pull falls back to {@link NoOpTenantListAuthProvider}, sends no token, and
 * jtenman answers 401.
 *
 * <pre>
 * jtenman:
 *   registry:
 *     client-registration-id: jtenman
 *
 * spring:
 *   security:
 *     oauth2:
 *       client:
 *         registration:
 *           jtenman:
 *             client-id: billing-svc
 *             client-secret: ${JTENMAN_SVC_TENANT_READ_SECRET}
 *             authorization-grant-type: client_credentials
 *         provider:
 *           jtenman:
 *             token-uri: https://keycloak.example.com/realms/master/protocol/openid-connect/token
 * </pre>
 * <p>
 * Three things about that configuration are decisions rather than boilerplate:
 * <ul>
 * <li>The <b>token URI is the administration realm</b>, not a tenant's. jtenman accepts tokens from the
 * one realm it is pinned to, so the service account lives there - it is an account of this application
 * against the control plane, not an account of any tenant.</li>
 * <li>The client needs an <b>audience mapper emitting {@code jtenman-api}</b> and the
 * {@code svc-tenant-read} realm role, granted through a group. Without the mapper the pull is a 401
 * even with a perfectly valid token; without the role it is a 403.</li>
 * <li>The <b>secret is a placeholder</b>, resolved from the environment or a secret store. It is the one
 * shared secret in this path, and it is why {@code private_key_jwt} is preferred wherever the
 * authorization server supports it - Spring reaches that by setting
 * {@code client-authentication-method: private_key_jwt} and adding a parameters converter, with no
 * change here.</li>
 * </ul>
 * <p>
 * A separate auto-configuration rather than a nested class, and ordered {@code before}
 * {@link TenantRegistryAutoConfiguration}, so that its {@code @ConditionalOnMissingBean} no-op provider
 * sees this one already registered and backs off. Two conditional beans of the same type in one
 * configuration class would leave which of them wins to the order Spring happens to parse them in.
 */
@AutoConfiguration(before = TenantRegistryAutoConfiguration.class)
@ConditionalOnClass({ OAuth2AuthorizedClientManager.class, ClientRegistrationRepository.class })
@ConditionalOnProperty(prefix = TenantRegistryProperties.PREFIX, name = "client-registration-id")
@EnableConfigurationProperties(TenantRegistryProperties.class)
public class TenantListClientCredentialsAutoConfiguration {

    /**
     * Creates the provider that obtains the service account's token.
     *
     * @param registrations Client registrations of the application, from
     *                      {@code spring.security.oauth2.client.registration}.
     * @param authorizedClients Store the obtained token is cached in until it expires.
     * @param properties Names the registration to use.
     *
     * @return Provider handing out the service account's token.
     */
    @Bean
    @ConditionalOnMissingBean(TenantListAuthProvider.class)
    public ClientCredentialsTenantListAuthProvider clientCredentialsTenantListAuthProvider(
            final ClientRegistrationRepository registrations,
            final OAuth2AuthorizedClientService authorizedClients,
            final TenantRegistryProperties properties) {

        // Deliberately not the auto-configured DefaultOAuth2AuthorizedClientManager: that one resolves
        // the servlet request and throws without one, and the pull runs on the refresh thread.
        final AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());

        return new ClientCredentialsTenantListAuthProvider(manager,
                properties.getClientRegistrationId());
    }

}

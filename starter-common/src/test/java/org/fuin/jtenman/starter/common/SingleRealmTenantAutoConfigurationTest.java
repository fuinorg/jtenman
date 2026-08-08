package org.fuin.jtenman.starter.common;

import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenant;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.SingleRealmTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.starter.KeycloakSecurityAutoConfiguration;
import org.fuin.ddd4j.core.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link SingleRealmTenantAutoConfiguration}.
 * <p>
 * Nothing here talks to Keycloak: {@link SingleRealmTenantRepository} resolves its tenant lazily, so the
 * admission decision - the only thing under test - is taken without OIDC discovery.
 */
class SingleRealmTenantAutoConfigurationTest {

    private static final String ISSUER = "http://localhost:8180/realms/master";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SingleRealmTenantAutoConfiguration.class))
            .withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=" + ISSUER);

    @Test
    void testRepositoryIsPinnedToTheConfiguredIssuer() {

        runner.run(context -> {
            assertThat(context).hasSingleBean(JwtTenantRepository.class);
            assertThat(context.getBean(JwtTenantRepository.class))
                    .isInstanceOf(SingleRealmTenantRepository.class);
            assertThat(context.getBean(SingleRealmTenantRepository.class).getIssuerUri()).isEqualTo(ISSUER);
        });

    }

    /**
     * The reason this auto-configuration exists. Loaded beside the keycloak starter, its repository has
     * to be the one that survives - the starter's {@link KeycloakTenantRepository} discovers and accepts
     * every realm of the instance, including the ones jtenman creates for its tenants.
     * <p>
     * Also guards the ordering: {@code @ConditionalOnMissingBean} in the starter only backs off if this
     * configuration was applied first, and a second {@link JwtTenantRepository} would break the
     * {@code hasSingleBean} assertion below.
     */
    @Test
    void testItReplacesTheDiscoveringRepositoryOfTheKeycloakStarter() {

        runner.withConfiguration(AutoConfigurations.of(KeycloakSecurityAutoConfiguration.class))
                // Mandatory for the starter, which refuses to start without an audience.
                .withPropertyValues("spring.security.oauth2.resourceserver.jwt.audiences=jtenman-api")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtTenantRepository.class);
                    assertThat(context).doesNotHaveBean(KeycloakTenantRepository.class);
                    assertThat(context.getBean(JwtTenantRepository.class))
                            .isInstanceOf(SingleRealmTenantRepository.class);
                });

    }

    /**
     * Conditional like every other bean in this project, so a test or a later control-plane-fed
     * implementation can take over without patching the starter.
     */
    @Test
    void testAnApplicationSuppliedRepositoryWins() {

        runner.withUserConfiguration(OwnRepositoryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(JwtTenantRepository.class);
            assertThat(context).doesNotHaveBean(SingleRealmTenantRepository.class);
        });

    }

    @Configuration(proxyBeanMethods = false)
    static class OwnRepositoryConfiguration {

        @Bean
        JwtTenantRepository ownRepository() {
            return new JwtTenantRepository() {

                @Override
                public Optional<JwtTenant> findByIssuer(final String issuerUri) {
                    return Optional.empty();
                }

                @Override
                public Stream<TenantId> getTenantIds() {
                    return Stream.empty();
                }

            };
        }

    }

}

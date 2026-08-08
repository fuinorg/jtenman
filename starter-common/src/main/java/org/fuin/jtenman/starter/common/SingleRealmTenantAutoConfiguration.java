package org.fuin.jtenman.starter.common;

import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.SingleRealmTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.starter.KeycloakSecurityAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Pins jtenman's trust boundary to the single realm it administers from.
 * <p>
 * jtenman uses the same Keycloak starter as the applications it administers, but is <b>not</b>
 * multi-tenant. The starter's default {@link KeycloakTenantRepository} discovers every realm below the
 * configured issuer's base URI on demand and accepts it - <b>regardless of
 * {@code org.fuin.cqrs4j.multitenancy}</b>. Without the bean declared here, jtenman would therefore
 * accept a token from any realm of its Keycloak instance, including the realms it creates for tenants:
 * the control plane would inherit the exact hole it exists to close.
 * <p>
 * This is deliberately an auto-configuration rather than something each deployable imports. All four
 * deployables (combined, command/server, query/server, process/server) reach it through their jtenman
 * starter, and a fifth one added later gets it without anyone remembering to - the failure mode of a
 * forgotten import is a silently discovering repository, which is the one failure this class exists to
 * prevent.
 * <p>
 * It must be ordered {@code before} {@link KeycloakSecurityAutoConfiguration}, whose own repository bean
 * is {@code @ConditionalOnMissingBean(JwtTenantRepository.class)} and therefore has to see this one
 * already registered to back off. Getting that order wrong leaves two {@link JwtTenantRepository} beans
 * and the context fails to start on the ambiguous injection into the issuer validator - loudly, not
 * silently, but wrong all the same.
 * <p>
 * <b>This module is jtenman-internal.</b> An application administered <i>by</i> jtenman must keep the
 * discovering (or, later, the jtenman-fed) repository - pinning it to one realm would make it reject
 * every one of its tenants.
 */
@AutoConfiguration(before = KeycloakSecurityAutoConfiguration.class)
public class SingleRealmTenantAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SingleRealmTenantAutoConfiguration.class);

    /**
     * Creates the tenant repository that accepts exactly the configured issuer and nothing else.
     * Declared {@link ConditionalOnMissingBean} like every other bean in this project, so a test or a
     * future control-plane-fed implementation can still replace it.
     *
     * @param issuerUri Issuer URI of the administration realm, from
     *                  {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}. No default on
     *                  purpose: a missing issuer must fail the context, not fall back to something.
     *
     * @return Repository pinned to that one issuer.
     */
    @Bean
    @ConditionalOnMissingBean(JwtTenantRepository.class)
    public SingleRealmTenantRepository singleRealmTenantRepository(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") final String issuerUri) {
        // Logged at INFO because it is the whole trust boundary of the control plane in one line, and
        // the only way to tell from a running instance which realm it actually accepts.
        LOG.info("Tenant trust boundary pinned to the single issuer '{}' - every other realm of this "
                + "Keycloak instance is rejected", issuerUri);
        return new SingleRealmTenantRepository(issuerUri);
    }

}

package org.fuin.jtenman.starter;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fuin.cqrs4j.springboot.keycloak.core.JwtTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.core.KeycloakTenantRepository;
import org.fuin.cqrs4j.springboot.keycloak.starter.KeycloakSecurityAutoConfiguration;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.fuin.ddd4j.jackson.Ddd4JacksonModule;
import org.fuin.ddd4j.jackson.JandexJacksonModule;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantControllerApi;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantService;
import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantServiceRestClient;
import org.fuin.jtenman.shared.JtenmanJacksonModule;
import org.fuin.jtenman.shared.domain.tenants.ApplicationId;
import org.fuin.objects4j.jackson.Objects4JJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.client.support.RestClientAdapter;

import java.util.List;

/**
 * Makes an application take its tenants from jtenman instead of discovering them.
 * <p>
 * This is the whole of what a consuming application adds. It declares a {@link JwtTenantRepository} fed
 * by jtenman's tenant list, so everything downstream of it - issuer validation, key selection,
 * per-tenant datasource routing, projections - keeps working unchanged and now has admission control and
 * revocation underneath it.
 * <p>
 * Ordered {@code before} {@link KeycloakSecurityAutoConfiguration}, whose own
 * {@link KeycloakTenantRepository} bean is {@code @ConditionalOnMissingBean(JwtTenantRepository.class)}
 * and therefore has to see this one already registered to back off. Get that order wrong and the context
 * fails on an ambiguous injection into the issuer validator - loudly, but wrong all the same.
 *
 * <h2>Configuration</h2>
 *
 * <pre>
 * jtenman:
 *   registry:
 *     url: https://jtenman.example.com
 *     application: billing        # this application's id in jtenman's catalogue
 * </pre>
 * <p>
 * Neither has a default; see {@link TenantRegistryProperties}. The remaining settings - refresh
 * interval, staleness bound, timeouts - do.
 *
 * <p><b>Nothing here is contributed until {@code url} is set</b>, so an application can carry this starter
 * on its class path and stay on whatever tenant repository it had. That matters because taking this one
 * changes where an application's trust boundary lives: instead of an issuer in its own configuration, the
 * set of realms it accepts tokens from becomes whatever the registry says, and its authentication then
 * depends on the registry being reachable - {@link JtenmanTenantRepository#usable()} is false until the
 * first successful pull and false again once the list is staler than the bound. An application should
 * decide that deliberately, which it cannot do if adding a dependency is enough to switch it.
 *
 * <h2>Why this starter brings its own object mapper</h2>
 * <p>
 * A {@code TenantDetails} is made of value objects, and Jackson needs the matching modules to read one:
 * without them a realm name arrives as {@code {"value":"acme","baseType":"java.lang.String"}} and the
 * pull fails. Registering those modules as beans would put them on the <em>application's</em> mapper,
 * which is not this starter's to change - and it would collide with the application's own scanning
 * module, since {@code @ConditionalOnMissingBean} matches by type. The mapper built here is private to
 * the tenant-list call and nothing else sees it.
 */
@AutoConfiguration(before = KeycloakSecurityAutoConfiguration.class)
@ConditionalOnProperty(prefix = TenantRegistryProperties.PREFIX, name = "url")
@EnableConfigurationProperties(TenantRegistryProperties.class)
public class TenantRegistryAutoConfiguration {

    /**
     * Creates the default provider of the token the tenant list is fetched with - which sends none.
     * Declare a {@link TenantListAuthProvider} bean to replace it.
     *
     * @return No-op provider.
     */
    @Bean
    @ConditionalOnMissingBean(TenantListAuthProvider.class)
    public NoOpTenantListAuthProvider noOpTenantListAuthProvider() {
        return new NoOpTenantListAuthProvider();
    }

    /**
     * Creates the HTTP proxy for jtenman's read model, from the generated {@code @HttpExchange}
     * contract.
     * <p>
     * Both timeouts are set on purpose. The pull runs on a single refresh thread, so a jtenman that
     * accepts a connection and then never answers would stop the replica from ever being refreshed
     * again - the one failure mode that turns this into a snapshot nobody notices is stale.
     *
     * @param properties Where jtenman is and how long to wait for it.
     * @param authProvider Supplies the token to send, if any.
     *
     * @return Proxy for the tenant list endpoint.
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantControllerApi tenantControllerApi(final TenantRegistryProperties properties,
            final TenantListAuthProvider authProvider) {

        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        final RestClient restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters
                        .add(0, new MappingJackson2HttpMessageConverter(tenantListObjectMapper())))
                .requestInitializer(request -> authProvider.bearerToken()
                        .ifPresent(token -> request.getHeaders().setBearerAuth(token)))
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(TenantControllerApi.class);
    }

    /**
     * Creates the read model as plain Java, satisfied over HTTP by the generated client.
     *
     * @param api Proxy for the tenant list endpoint.
     *
     * @return Read model of jtenman.
     */
    @Bean
    @ConditionalOnMissingBean
    public TenantService jtenmanTenantService(final TenantControllerApi api) {
        return new TenantServiceRestClient(api);
    }

    /**
     * Creates the repository that answers which realms are tenants of this application.
     *
     * @param tenantService Read model of jtenman.
     * @param properties Which application to ask for, and how long a list may go unrefreshed.
     * @param publisher Used to announce tenants that appeared and disappeared.
     *
     * @return Repository fed by the control plane.
     */
    @Bean
    @ConditionalOnMissingBean(JwtTenantRepository.class)
    public JtenmanTenantRepository jtenmanTenantRepository(final TenantService tenantService,
            final TenantRegistryProperties properties, final ApplicationEventPublisher publisher) {
        return new JtenmanTenantRepository(tenantService, new ApplicationId(properties.getApplication()),
                properties.getMaxStaleness(), publisher);
    }

    /**
     * Creates the loop that keeps the replica up to date. Spring closes it by calling the declared
     * destroy method.
     *
     * @param repository Replica to refresh.
     * @param properties How often to refresh it.
     *
     * @return Running refresher.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public TenantRegistryRefresher tenantRegistryRefresher(final JtenmanTenantRepository repository,
            final TenantRegistryProperties properties) {
        return new TenantRegistryRefresher(repository, properties.getRefreshInterval());
    }

    /**
     * Builds the mapper used for the tenant list and nothing else - see the class comment for why it is
     * not a bean.
     *
     * @return Mapper that can read a {@code TenantDetails}.
     */
    private static ObjectMapper tenantListObjectMapper() {
        return new ObjectMapper()
                // The value objects are single-field wrappers with no getters; the event store side of
                // jtenman reads them the same way.
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                // A field jtenman adds later must not break the pull of an application that has not been
                // rebuilt - the fields this starter needs are the ones it already knows.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModules(List.of(
                        new JtenmanJacksonModule(),
                        new Objects4JJacksonModule(),
                        new Ddd4JacksonModule(new JandexEntityIdFactory()),
                        new JandexJacksonModule("org.fuin.jtenman")));
    }

}

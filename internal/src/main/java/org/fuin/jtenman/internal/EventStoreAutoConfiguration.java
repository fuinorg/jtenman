package org.fuin.jtenman.internal;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBClientSettings;
import org.fuin.cqrs4j.jackson.Cqrs4JacksonModule;
import org.fuin.ddd4j.core.EntityIdFactory;
import org.fuin.ddd4j.core.JandexEntityIdFactory;
import org.fuin.ddd4j.jackson.Ddd4JacksonModule;
import org.fuin.ddd4j.jackson.JandexJacksonModule;
import org.fuin.esc.api.ConverterRegistry;
import org.fuin.esc.api.EnhancedMimeType;
import org.fuin.esc.api.SerDeserializerRegistry;
import org.fuin.esc.api.SerializedDataTypeRegistry;
import org.fuin.esc.api.SimpleConverterRegistry;
import org.fuin.esc.api.SimpleSerializerDeserializerRegistry;
import org.fuin.esc.api.UpcastingDeserializerRegistry;
import org.fuin.esc.client.JandexSerializedDataTypeRegistry;
import org.fuin.esc.esgrpc.ESGrpcEventStore;
import org.fuin.esc.esgrpc.IESGrpcEventStore;
import org.fuin.esc.jackson.BaseTypeFactory;
import org.fuin.esc.jackson.EscJacksonModule;
import org.fuin.esc.jackson.EscJacksonUtils;
import org.fuin.esc.jackson.JacksonSerDeserializer;
import org.fuin.jtenman.shared.JtenmanJacksonModule;
import org.fuin.objects4j.jackson.ImmutableObjectMapper;
import org.fuin.objects4j.jackson.Objects4JJacksonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Wires the KurrentDB backed event store used by the command side. Everything is declared
 * {@link ConditionalOnMissingBean} so an application - or another jtenman starter loaded beside
 * this one in the combined deployable - can replace any single bean without losing the rest.
 */
@AutoConfiguration
@EnableConfigurationProperties(EventStoreProperties.class)
public class EventStoreAutoConfiguration {

    /**
     * Creates the factory that resolves entity identifiers by scanning the Jandex index.
     *
     * @return Entity id factory.
     */
    @Bean
    @ConditionalOnMissingBean
    public EntityIdFactory entityIdFactory() {
        return new JandexEntityIdFactory();
    }

    /**
     * Registers the JSON decisions that apply to this project as a whole.
     * <p>
     * Every module below is a bean rather than an inline {@code new ...()} so that Spring Boot's Jackson
     * auto-configuration finds it: it collects all {@link Module} beans and registers them on the mapper
     * that Spring MVC uses. Without that, the event store would speak one dialect and the REST layer
     * another - a value object would go into the event store as "Office supplies" and come out of a
     * query endpoint as {@code {"baseType":"java.lang.String"}}.
     *
     * @return Module for this project's JSON conventions.
     */
    @Bean
    @ConditionalOnMissingBean
    public JtenmanJacksonModule jtenmanJacksonModule() {
        return new JtenmanJacksonModule();
    }

    /**
     * Registers the (de)serializers for this project's value objects, discovered through the Jandex index.
     * <p>
     * A value object is one value, not a bean: without a registration Jackson writes a category name as
     * {@code {"value":"Office supplies","baseType":"java.lang.String"}} and then refuses to read it back.
     * Scanning rather than listing means a value object added to a {@code .cqrs} model needs no follow-up
     * here. The identifiers are not part of this - {@link #ddd4JacksonModule(EntityIdFactory)} covers them.
     * <p>
     * The scan is restricted to this project's own package on purpose. The libraries on the class path
     * carry value objects too, and several of them are already registered by their own module above -
     * {@code CurrencyAmount} by {@link Objects4JJacksonModule}, {@code TypeName} by {@code EscJacksonModule}.
     * Registering those a second time would leave it to the order in which Spring hands the modules to the
     * mapper which registration wins.
     *
     * @return Module for the value objects of this project.
     */
    @Bean
    @ConditionalOnMissingBean
    public JandexJacksonModule jandexJacksonModule() {
        return new JandexJacksonModule("org.fuin.jtenman");
    }

    /**
     * Registers the (de)serializers for the cqrs4j types (a command's result, for example).
     *
     * @return Module for the cqrs4j types.
     */
    @Bean
    @ConditionalOnMissingBean
    public Cqrs4JacksonModule cqrs4JacksonModule() {
        return new Cqrs4JacksonModule();
    }

    /**
     * Registers the (de)serializers for the objects4j types (a currency amount, for example).
     *
     * @return Module for the objects4j types.
     */
    @Bean
    @ConditionalOnMissingBean
    public Objects4JJacksonModule objects4JJacksonModule() {
        return new Objects4JJacksonModule();
    }

    /**
     * Registers the (de)serializers for the ddd4j types - an entity identifier and the path to it.
     *
     * @param entityIdFactory Factory used to deserialize entity identifiers.
     *
     * @return Module for the ddd4j types.
     */
    @Bean
    @ConditionalOnMissingBean
    public Ddd4JacksonModule ddd4JacksonModule(final EntityIdFactory entityIdFactory) {
        return new Ddd4JacksonModule(entityIdFactory);
    }

    /**
     * Creates the builder for the immutable object mapper shared by all serializers. It gets the same
     * modules as the mapper of the REST layer, plus the settings the event store needs.
     *
     * @param modules Modules registered on every mapper of this application.
     *
     * @return Object mapper builder.
     */
    @Bean
    @ConditionalOnMissingBean
    public ImmutableObjectMapper.Builder immutableObjectMapperBuilder(final List<Module> modules) {
        return new ImmutableObjectMapper.Builder(new ObjectMapper()
                // A rate or an amount must read back exactly as it was written, never as 1.9E+1.
                .enable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .registerModules(modules));
    }

    /**
     * Creates the provider that hands out the (then immutable) object mapper.
     *
     * @param mapperBuilder Builder to wrap.
     *
     * @return Object mapper provider.
     */
    @Bean
    @ConditionalOnMissingBean
    public ImmutableObjectMapper.Provider immutableObjectMapperProvider(
            final ImmutableObjectMapper.Builder mapperBuilder) {
        return new ImmutableObjectMapper.Provider(mapperBuilder);
    }

    /**
     * Creates the registry of all serialized data types, discovered through the Jandex index.
     *
     * @return Type registry.
     */
    @Bean
    @ConditionalOnMissingBean
    public SerializedDataTypeRegistry serializedDataTypeRegistry() {
        return new JandexSerializedDataTypeRegistry();
    }

    /**
     * Creates the Jackson based serializer/deserializer used for every known type.
     *
     * @param mapperProvider Object mapper provider.
     * @param typeRegistry Registry of the known types.
     *
     * @return Serializer/deserializer.
     */
    @Bean
    @ConditionalOnMissingBean
    public JacksonSerDeserializer jacksonSerDeserializer(final ImmutableObjectMapper.Provider mapperProvider,
            final SerializedDataTypeRegistry typeRegistry) {
        return new JacksonSerDeserializer.Builder()
                .withObjectMapper(mapperProvider)
                .withTypeRegistry(typeRegistry)
                .withEncoding(StandardCharsets.UTF_8)
                .build();
    }

    /**
     * Registers the Jackson serializer for every known type and adds the ESC module to the mapper.
     *
     * @param typeRegistry Registry of the known types.
     * @param jacksonSerDeserializer Serializer/deserializer to register for each type.
     * @param mapperBuilder Builder the ESC module is added to.
     *
     * @return Serializer/deserializer registry.
     */
    @Bean
    @ConditionalOnMissingBean
    public SerDeserializerRegistry serDeserializerRegistry(final SerializedDataTypeRegistry typeRegistry,
            final JacksonSerDeserializer jacksonSerDeserializer,
            final ImmutableObjectMapper.Builder mapperBuilder) {
        final SimpleSerializerDeserializerRegistry.Builder builder =
                new SimpleSerializerDeserializerRegistry.Builder(EscJacksonUtils.MIME_TYPE);
        for (final SerializedDataTypeRegistry.TypeClass tc : typeRegistry.findAll()) {
            builder.add(tc.type(), jacksonSerDeserializer);
        }
        final SerDeserializerRegistry registry = builder.build();
        mapperBuilder.registerModule(new EscJacksonModule(registry, registry));
        return registry;
    }

    /**
     * Creates the registry of the up-casters applied while deserializing. It is empty until the
     * first event needs a version migration; adding a converter later replaces this bean.
     *
     * @return Converter registry.
     */
    @Bean
    @ConditionalOnMissingBean
    public ConverterRegistry converterRegistry() {
        return new SimpleConverterRegistry.Builder().build();
    }

    /**
     * Creates the KurrentDB client. Spring closes it by calling the declared destroy method.
     *
     * @param config Connection settings.
     *
     * @return KurrentDB client.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public KurrentDBClient kurrentDBClient(final EventStoreProperties config) {
        final KurrentDBClientSettings settings = KurrentDBClientSettings.builder()
                .addHost(config.getHost(), config.getPort())
                .defaultCredentials(config.getUser(), config.getPassword())
                .tls(config.isTls())
                .buildConnectionSettings();
        return KurrentDBClient.create(settings);
    }

    /**
     * Creates the synchronous event store the aggregate repositories write to and read from.
     *
     * @param registry Serializer/deserializer registry.
     * @param converterRegistry Registry of the up-casters applied while deserializing.
     * @param client KurrentDB client to use.
     *
     * @return Opened event store.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(IESGrpcEventStore.class)
    public IESGrpcEventStore eventStore(final SerDeserializerRegistry registry,
            final ConverterRegistry converterRegistry, final KurrentDBClient client) {
        return new ESGrpcEventStore.Builder()
                .eventStore(client)
                .serRegistry(registry)
                .desRegistry(new UpcastingDeserializerRegistry(registry, converterRegistry))
                .baseTypeFactory(new BaseTypeFactory())
                .targetContentType(EnhancedMimeType.create("application", "json", StandardCharsets.UTF_8))
                .build()
                .open();
    }

}

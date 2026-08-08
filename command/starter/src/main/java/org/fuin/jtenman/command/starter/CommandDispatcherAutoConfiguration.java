package org.fuin.jtenman.command.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.fuin.cqrs4j.core.CommandAuthorizer;
import org.fuin.cqrs4j.core.CommandHandlerRegistry;
import org.fuin.cqrs4j.core.JandexCommandHandlerRegistry;
import org.fuin.cqrs4j.core.SimpleCommandAuthorizer;
import org.fuin.cqrs4j.springboot.command.core.CommandDispatcher;
import org.fuin.esc.api.DeserializerRegistry;
import org.fuin.cqrs4j.springboot.command.starter.CommandEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.Optional;

/**
 * Wires the command dispatching the generic command endpoint of the cqrs4j command starter needs.
 * Providing a {@link CommandDispatcher} is what activates {@code POST /cmd/{type}}.
 */
// Must be evaluated BEFORE the cqrs4j endpoint configuration: that one contributes POST /cmd/{type}
// only @ConditionalOnBean(CommandDispatcher.class), so if this configuration has not run yet the
// condition finds nothing and the endpoint silently does not exist - a 404 with no hint as to why.
@AutoConfiguration(before = CommandEndpointAutoConfiguration.class)
public class CommandDispatcherAutoConfiguration {

    /**
     * Creates the authorizer deciding who may execute which command.
     *
     * <p>Every command currently maps to an empty role list, which
     * {@link SimpleCommandAuthorizer} reads as "everyone is allowed". A <em>missing</em> entry would
     * mean "denied", so this is deliberately an allow-all placeholder until the roles per command
     * are modelled - replace this bean to enforce them.
     *
     * @return Command authorizer.
     */
    @Bean
    @ConditionalOnMissingBean
    public CommandAuthorizer commandAuthorizer() {
        return new SimpleCommandAuthorizer(cmdClass -> Optional.of(List.of()));
    }

    /**
     * Creates the registry that finds a command's handler through the Jandex index.
     *
     * @return Command handler registry.
     */
    @Bean
    @ConditionalOnMissingBean
    public CommandHandlerRegistry commandHandlerRegistry() {
        return new JandexCommandHandlerRegistry();
    }

    /**
     * Creates the dispatcher that deserializes, validates, authorizes and executes a command. It is
     * request scoped because it carries the caller of the request being served.
     *
     * @param objectMapper Mapper used to deserialize the command.
     * @param deserializerRegistry Registry of the known command types.
     * @param commandAuthorizer Decides whether the caller may execute the command.
     * @param validator Validates the deserialized command.
     * @param commandHandlerRegistry Finds the handler for a command.
     * @param context Context the handler bean is taken from.
     *
     * @return Command dispatcher.
     */
    @Bean
    @RequestScope
    @ConditionalOnMissingBean
    public CommandDispatcher commandDispatcher(final ObjectMapper objectMapper,
            final DeserializerRegistry deserializerRegistry, final CommandAuthorizer commandAuthorizer,
            final Validator validator, final CommandHandlerRegistry commandHandlerRegistry,
            final ApplicationContext context) {
        return new CommandDispatcher(objectMapper, deserializerRegistry, commandAuthorizer, validator,
                commandHandlerRegistry, context);
    }

}

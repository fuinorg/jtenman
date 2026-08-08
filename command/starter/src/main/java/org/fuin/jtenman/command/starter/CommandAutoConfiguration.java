package org.fuin.jtenman.command.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Wires the write side. The generated beans are listed in {@link CommandBeansConfiguration}, which is
 * regenerated from the models on every build; the hand-written ones - the command handlers and the
 * Keycloak adapter they need - will be listed per context beside it. An application only depends on this
 * starter and never component-scans the generated packages.
 * <p>
 * <b>Every context's configuration has to be listed below.</b> Nothing is component-scanned, so a
 * configuration class that exists but is not imported is simply never loaded - and because the dispatcher
 * only asks for a handler when a command of that type actually arrives, the application still starts,
 * still passes its health check, and fails with {@code NoSuchBeanDefinitionException} the first time
 * somebody sends that command. Add the tenants configuration here the moment its handlers exist, and a
 * test that asserts a handler bean per command type alongside it.
 */
@AutoConfiguration
@Import({CommandBeansConfiguration.class, TenantCommandConfiguration.class})
public class CommandAutoConfiguration {

}

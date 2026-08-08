package org.fuin.jtenman.command.starter;

import org.fuin.jtenman.command.core.domain.tenants.TenantRepositoryFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registers the write side beans explicitly, replacing a component scan of the generated
 * packages. Each imported class is itself a {@code @Configuration} producing one
 * aggregate repository. Regenerated on every build.
 */
@Configuration
@Import({TenantRepositoryFactory.class})
public class CommandBeansConfiguration {

}

package org.fuin.jtenman.query.starter;

import jakarta.persistence.EntityManager;
import org.fuin.jtenman.query.core.view.tenants.tenantview.TenantController;
import org.fuin.jtenman.query.core.view.tenants.tenantview.TenantServiceImpl;
import org.fuin.jtenman.query.core.view.tenants.tenantview.TenantView;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

/**
 * Registers the read side beans explicitly, replacing a component scan of the generated
 * packages. Regenerated on every build.
 *
 * <p>Each view contributes two: the generated controller exposing it over REST, and the
 * hand-written service implementation the controller forwards to. Both are injected by
 * type, so importing the classes is enough.
 */
@Configuration
@Import({TenantController.class, TenantServiceImpl.class})
public class QueryBeansConfiguration {

    /**
     * Creates a TenantView instance. The view manager looks the bean up by the
     * name the view reports, and creates one instance per projection run, hence the
     * prototype scope.
     *
     * @param em Entity manager used to store the read model.
     *
     * @return New view instance.
     */
    @Bean(TenantView.BEAN_NAME)
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public TenantView tenantView(final EntityManager em) {
        return new TenantView(em);
    }

}

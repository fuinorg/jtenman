package org.fuin.jtenman.query.starter;

import jakarta.persistence.EntityManager;
import org.fuin.jtenman.query.core.view.tenants.tenantview.TenantController;
import org.fuin.jtenman.query.core.view.tenants.tenantview.TenantServiceImpl;
import org.fuin.jtenman.query.core.view.tenants.tenantview.TenantView;
import org.springframework.beans.factory.annotation.Qualifier;
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
     * @param em Entity manager used to store the read model. Asked for by name rather
     *           than by type, because an application may keep the read model in a different
     *           database from the rest of its persistence - an in-memory one rebuilt from
     *           the event store at every start, for instance - and then a bare
     *           {@code EntityManager} does not say which of the two is meant. The query
     *           starter declares this bean, so an application with one datasource needs to
     *           do nothing.
     *
     * @return New view instance.
     */
    @Bean(TenantView.BEAN_NAME)
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    public TenantView tenantView(
            @Qualifier("readModelEntityManager") final EntityManager em) {
        return new TenantView(em);
    }

}

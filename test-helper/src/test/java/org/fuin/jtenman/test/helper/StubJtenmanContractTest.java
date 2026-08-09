package org.fuin.jtenman.test.helper;

import org.fuin.jtenman.query.api.view.tenants.tenantview.TenantControllerApi;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link StubJtenman#PATH} to the generated REST contract.
 * <p>
 * The stub serves a literal path so that this module does not force {@code spring-web} onto consumers -
 * jtenman's contract is generated in a Quarkus flavour as well. A literal can drift, and this is the
 * drift that would hurt: the model moves the view, the contract is regenerated, and every consumer's
 * stubbed test keeps passing against a path nothing calls any more.
 * <p>
 * {@code spring-web} and {@code jtenman-query-api} are test-scoped for exactly this one check.
 */
class StubJtenmanContractTest {

    @Test
    void testTheStubServesThePathTheGeneratedContractDeclares() throws Exception {

        // GIVEN
        final HttpExchange onType = TenantControllerApi.class.getAnnotation(HttpExchange.class);
        final Method listByApplication = TenantControllerApi.class.getMethod(
                "listByApplication", org.fuin.jtenman.shared.domain.tenants.ApplicationId.class);
        final GetExchange onMethod = listByApplication.getAnnotation(GetExchange.class);

        // WHEN
        final String declared = onType.value() + onMethod.value();

        // THEN
        assertThat(declared).isEqualTo(StubJtenman.PATH);

    }

    @Test
    void testTheStubReadsTheParameterTheGeneratedContractDeclares() throws Exception {

        // GIVEN
        final Method listByApplication = TenantControllerApi.class.getMethod(
                "listByApplication", org.fuin.jtenman.shared.domain.tenants.ApplicationId.class);
        final Parameter parameter = listByApplication.getParameters()[0];

        // WHEN
        final RequestParam requestParam = parameter.getAnnotation(RequestParam.class);

        // THEN
        assertThat(requestParam).isNotNull();
        assertThat(requestParam.value()).isEqualTo(StubJtenman.APPLICATION_PARAMETER);

    }

}

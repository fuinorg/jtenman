package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import java.util.Objects;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * The tenant is already subscribed to that application. Subscribing twice would create a second Keycloak client for the same purpose.
 */
public final class ApplicationAlreadySubscribedException extends Exception {

    @Serial
    private static final long serialVersionUID = 1000L;

    private String realm;
    
    private String application;
    
    /**
     * Constructs a new instance of the exception.
     *
     * @param realm The realm of the tenant.
     * @param application The application the tenant already uses.
     */
    public ApplicationAlreadySubscribedException(final String realm, final String application) {
        super(Objects.requireNonNull(KeyValueEL.replace("Tenant '${realm}' is already subscribed to application '${application}'",  new KeyValue("realm", realm), new KeyValue("application", application))));
        Contract.requireArgNotNull("realm", realm);
        Contract.requireArgNotNull("application", application);
        
        this.realm = realm;
        this.application = application;
    }

    /**
     * Returns: The realm of the tenant.
     *
     * @return Current value.
     */
    public final String getRealm() {
        return realm;
    }
    
    /**
     * Returns: The application the tenant already uses.
     *
     * @return Current value.
     */
    public final String getApplication() {
        return application;
    }
    
}

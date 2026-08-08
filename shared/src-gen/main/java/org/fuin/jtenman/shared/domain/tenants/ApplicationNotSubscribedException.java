package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import java.util.Objects;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * The tenant does not use that application, so there is nothing to unsubscribe from.
 */
public final class ApplicationNotSubscribedException extends Exception {

    @Serial
    private static final long serialVersionUID = 1000L;

    private String realm;
    
    private String application;
    
    /**
     * Constructs a new instance of the exception.
     *
     * @param realm The realm of the tenant.
     * @param application The application the tenant does not use.
     */
    public ApplicationNotSubscribedException(final String realm, final String application) {
        super(Objects.requireNonNull(KeyValueEL.replace("Tenant '${realm}' is not subscribed to application '${application}'",  new KeyValue("realm", realm), new KeyValue("application", application))));
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
     * Returns: The application the tenant does not use.
     *
     * @return Current value.
     */
    public final String getApplication() {
        return application;
    }
    
}

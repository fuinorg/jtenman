package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import java.util.Objects;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * A tenant is already registered for that realm. The realm name is the tenant's identity, so it can only be registered once.
 */
public final class TenantAlreadyRegisteredException extends Exception {

    @Serial
    private static final long serialVersionUID = 1000L;

    private String realm;
    
    /**
     * Constructs a new instance of the exception.
     *
     * @param realm The realm that is already registered.
     */
    public TenantAlreadyRegisteredException(final String realm) {
        super(Objects.requireNonNull(KeyValueEL.replace("A tenant is already registered for realm '${realm}'",  new KeyValue("realm", realm))));
        Contract.requireArgNotNull("realm", realm);
        
        this.realm = realm;
    }

    /**
     * Returns: The realm that is already registered.
     *
     * @return Current value.
     */
    public final String getRealm() {
        return realm;
    }
    
}

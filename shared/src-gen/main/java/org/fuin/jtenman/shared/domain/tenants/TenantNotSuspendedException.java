package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import java.util.Objects;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * A tenant was deleted while it was still active. Deleting is irreversible, so it is only allowed once access has been revoked and that revocation has reached every application.
 */
public final class TenantNotSuspendedException extends Exception {

    @Serial
    private static final long serialVersionUID = 1000L;

    private String realm;
    
    /**
     * Constructs a new instance of the exception.
     *
     * @param realm The realm that is still active.
     */
    public TenantNotSuspendedException(final String realm) {
        super(Objects.requireNonNull(KeyValueEL.replace("Tenant '${realm}' must be suspended before it can be deleted",  new KeyValue("realm", realm))));
        Contract.requireArgNotNull("realm", realm);
        
        this.realm = realm;
    }

    /**
     * Returns: The realm that is still active.
     *
     * @return Current value.
     */
    public final String getRealm() {
        return realm;
    }
    
}

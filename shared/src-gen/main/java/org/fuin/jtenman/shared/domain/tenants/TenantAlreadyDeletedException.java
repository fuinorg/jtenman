package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import java.util.Objects;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.core.KeyValue;
import org.fuin.objects4j.core.KeyValueEL;

/**
 * An operation was attempted on a tenant that has already been deleted. <p> Deleting removes the aggregate as well as recording the fact, so normally the tenant cannot be reached at all. This covers the window in between: recording the deletion and removing the aggregate are two calls with no shared transaction, so a tenant can survive its own deletion - and it must not act afterwards, least of all against a realm that is already gone.
 */
public final class TenantAlreadyDeletedException extends Exception {

    @Serial
    private static final long serialVersionUID = 1000L;

    private String realm;
    
    /**
     * Constructs a new instance of the exception.
     *
     * @param realm The realm of the deleted tenant.
     */
    public TenantAlreadyDeletedException(final String realm) {
        super(Objects.requireNonNull(KeyValueEL.replace("Tenant '${realm}' has been deleted",  new KeyValue("realm", realm))));
        Contract.requireArgNotNull("realm", realm);
        
        this.realm = realm;
    }

    /**
     * Returns: The realm of the deleted tenant.
     *
     * @return Current value.
     */
    public final String getRealm() {
        return realm;
    }
    
}

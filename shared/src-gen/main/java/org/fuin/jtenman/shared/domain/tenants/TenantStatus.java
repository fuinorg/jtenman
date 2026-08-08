package org.fuin.jtenman.shared.domain.tenants;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Whether a tenant may currently be used. A suspended tenant keeps its subscriptions, so resuming restores exactly the previous set rather than requiring each application to be subscribed again. */
public enum TenantStatus {
    
    /** The tenant is usable by every application it is subscribed to. */
    ACTIVE,
    
        /** Access is revoked everywhere; the realm is disabled in Keycloak. */
    SUSPENDED
    
    ;
    
    /** All instances. */
    public static final List<TenantStatus> ALL = List.of(
        ACTIVE, SUSPENDED
    );
    
    /** Valid instances. */
    public static final List<TenantStatus> VALID = List.of(
        ACTIVE, SUSPENDED
    );
    
    /** Deprecated instances. */
    public static final List<TenantStatus> DEPRECATED = List.of(
    );
    
}

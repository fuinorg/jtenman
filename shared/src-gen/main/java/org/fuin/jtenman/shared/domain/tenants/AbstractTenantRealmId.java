package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import org.fuin.ddd4j.core.AggregateRootId;
import org.fuin.ddd4j.core.EntityType;
import org.fuin.ddd4j.core.StringBasedEntityType;
import org.fuin.objects4j.common.ValueObject;
import org.fuin.objects4j.core.AbstractStringValueObject;

/**
 * Uniquely identifies a tenant by the name of its Keycloak realm.
 */
public abstract class AbstractTenantRealmId extends AbstractStringValueObject implements AggregateRootId, ValueObject {

    @Serial
    private static final long serialVersionUID = 1000L;
    
    /** Name that identifies the entity uniquely within the context. */    
    public static final EntityType TYPE = new StringBasedEntityType("Tenant");
    
    @Override
    public final EntityType getType() {
        return TYPE;
    }
    
    @Override
    public final String asTypedString() {
        return TYPE + " " + asString();
    }
    
}

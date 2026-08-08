package org.fuin.jtenman.shared.domain.tenants;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import org.fuin.dsl.cqrs.common.basics.VersionedEntityIdPath;
import org.fuin.objects4j.common.Contract;
import org.fuin.objects4j.common.ValueObject;

/**
 * A read-model row describing one tenant of one application - the answer a consuming application replicates to decide which issuers it trusts.
 */
public abstract class AbstractTenantDetails implements ValueObject, Serializable {

    @Serial
    private static final long serialVersionUID = 1000L;
    
    private VersionedEntityIdPath source;
    
    private RealmName realm;
    
    private IssuerUri issuerUri;
    
    private TenantStatus status;
    
    /**
     * Default constructor.
     */
    @SuppressWarnings("NullAway.Init")
    protected AbstractTenantDetails() {
        super();
    }
    
    /**
     * Constructor with all data.
     *
     * @param source Aggregate this row was projected from and the version it reflects.
     * @param realm Name of the tenant's realm.
     * @param issuerUri Issuer URI the tenant's tokens carry.
     * @param status Whether the tenant may currently be used.
     */
    public AbstractTenantDetails(final VersionedEntityIdPath source, final RealmName realm, final IssuerUri issuerUri, final TenantStatus status) {
        super();
        Contract.requireArgNotNull("source", source);
        Contract.requireArgNotNull("realm", realm);
        Contract.requireArgNotNull("issuerUri", issuerUri);
        Contract.requireArgNotNull("status", status);
        
        this.source = source;
        this.realm = realm;
        this.issuerUri = issuerUri;
        this.status = status;
    }
    
    /**
     * Returns: Aggregate this row was projected from and the version it reflects.
     *
     * @return Current value.
     */
    public final VersionedEntityIdPath getSource() {
        return source;
    }
    
    /**
     * Returns: Name of the tenant's realm.
     *
     * @return Current value.
     */
    public final RealmName getRealm() {
        return realm;
    }
    
    /**
     * Returns: Issuer URI the tenant's tokens carry.
     *
     * @return Current value.
     */
    public final IssuerUri getIssuerUri() {
        return issuerUri;
    }
    
    /**
     * Returns: Whether the tenant may currently be used.
     *
     * @return Current value.
     */
    public final TenantStatus getStatus() {
        return status;
    }
    
    @Override
    public final int hashCode() {
        return Objects.hash(source, realm, issuerUri, status);
    }
    
    @Override
    public final boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AbstractTenantDetails other = (AbstractTenantDetails) obj;
        return Objects.equals(source, other.source)
            && Objects.equals(realm, other.realm)
            && Objects.equals(issuerUri, other.issuerUri)
            && Objects.equals(status, other.status);
    }
}

package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * JPA entity for the tenant_applications table.
 */
@Table(name = "tenant_applications", indexes = { @Index(name = "ix_tenant_applications_app", columnList = "application", unique = false) })
@Entity
public class TenantApplicationEntity {

    @Id
    @Column(name = "id", nullable = false, length = 61)
    private String id;
    
    @Column(name = "realm", nullable = false, length = 10)
    private String realm;
    
    @Column(name = "application", nullable = false, length = 50)
    private String application;
    
    /**
     * Protected default constructor only required for JPA.
     */
    @SuppressWarnings("NullAway.Init")
    protected TenantApplicationEntity() {
        super();
    }

    public String getId() {
        return id;
    }
    
    public void setId(final String id) {
        this.id = id;
    }
    
    public String getRealm() {
        return realm;
    }
    
    public void setRealm(final String realm) {
        this.realm = realm;
    }
    
    public String getApplication() {
        return application;
    }
    
    public void setApplication(final String application) {
        this.application = application;
    }
    
}

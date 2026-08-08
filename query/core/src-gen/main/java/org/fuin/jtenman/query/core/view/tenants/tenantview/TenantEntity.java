package org.fuin.jtenman.query.core.view.tenants.tenantview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the tenants table.
 */
@Table(name = "tenants")
@Entity
public class TenantEntity {

    @Id
    @Column(name = "realm", nullable = false, length = 10)
    private String realm;
    
    @Column(name = "issuer_uri", nullable = false, length = 255)
    private String issuerUri;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    
    @Column(name = "entity_id_path", nullable = false, length = 255)
    private String entityIdPath;
    
    @Column(name = "aggregate_version", nullable = true)
    private Integer aggregateVersion;
    
    /**
     * Protected default constructor only required for JPA.
     */
    @SuppressWarnings("NullAway.Init")
    protected TenantEntity() {
        super();
    }

    public String getRealm() {
        return realm;
    }
    
    public void setRealm(final String realm) {
        this.realm = realm;
    }
    
    public String getIssuerUri() {
        return issuerUri;
    }
    
    public void setIssuerUri(final String issuerUri) {
        this.issuerUri = issuerUri;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(final String status) {
        this.status = status;
    }
    
    public String getEntityIdPath() {
        return entityIdPath;
    }
    
    public void setEntityIdPath(final String entityIdPath) {
        this.entityIdPath = entityIdPath;
    }
    
    public Integer getAggregateVersion() {
        return aggregateVersion;
    }
    
    public void setAggregateVersion(final Integer aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }
    
}

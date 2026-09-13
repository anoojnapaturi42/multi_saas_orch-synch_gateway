package com.example.gateway.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "integration_app", indexes = {
        @Index(name = "ix_integration_app_tenant", columnList = "tenant_id"),
        @Index(name = "ix_integration_app_auth_type", columnList = "auth_type")
})
public class IntegrationApp {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private Enums.AuthType authType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_headers", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> defaultHeaders = Map.of();

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IntegrationApp() {}

    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Enums.AuthType getAuthType() { return authType; }
    public void setAuthType(Enums.AuthType authType) { this.authType = authType; }
    public Map<String, Object> getDefaultHeaders() { return defaultHeaders; }
    public void setDefaultHeaders(Map<String, Object> defaultHeaders) { this.defaultHeaders = defaultHeaders; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

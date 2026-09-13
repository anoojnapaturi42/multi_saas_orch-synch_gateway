package com.example.gateway.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_definition", indexes = {
        @Index(name = "ix_workflow_definition_tenant_enabled", columnList = "tenant_id, is_enabled"),
        @Index(name = "ix_workflow_definition_trigger", columnList = "trigger_type")
}, uniqueConstraints = @UniqueConstraint(name = "uk_workflow_definition_tenant_name", columnNames = {"tenant_id", "name"}))
public class WorkflowDefinition {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 150) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false, length = 20)
    private Enums.TriggerType triggerType;
    @Column(name = "is_enabled", nullable = false) private boolean enabled = true;
    @Column(name = "tenant_id", nullable = false, length = 100) private String tenantId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_app_id", nullable = false)
    private IntegrationApp sourceApp;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected WorkflowDefinition() {}
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public Enums.TriggerType getTriggerType() { return triggerType; } public void setTriggerType(Enums.TriggerType v) { triggerType = v; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled = v; }
    public String getTenantId() { return tenantId; } public void setTenantId(String v) { tenantId = v; }
    public IntegrationApp getSourceApp() { return sourceApp; } public void setSourceApp(IntegrationApp v) { sourceApp = v; }
    public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}

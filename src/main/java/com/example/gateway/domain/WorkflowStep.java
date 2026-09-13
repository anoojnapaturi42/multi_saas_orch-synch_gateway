package com.example.gateway.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workflow_step", indexes = {
        @Index(name = "ix_workflow_step_workflow_order", columnList = "workflow_id, step_order"),
        @Index(name = "ix_workflow_step_target_app", columnList = "target_app_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_workflow_step_order", columnNames = {"workflow_id", "step_order"}))
public class WorkflowStep {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowDefinition workflow;
    @Column(name = "step_order", nullable = false) private int stepOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "target_app_id", nullable = false)
    private IntegrationApp targetApp;
    @Enumerated(EnumType.STRING) @Column(name = "http_method", nullable = false, length = 10) private Enums.HttpMethod httpMethod;
    @Column(name = "endpoint_path", nullable = false, length = 2048) private String endpointPath;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "transform_schema", columnDefinition = "jsonb", nullable = false) private Map<String, Object> transformSchema = Map.of();
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "retry_config", columnDefinition = "jsonb", nullable = false) private Map<String, Object> retryConfig = Map.of();
    @Enumerated(EnumType.STRING) @Column(name = "failure_strategy", nullable = false, length = 10) private Enums.FailureStrategy failureStrategy = Enums.FailureStrategy.ABORT;
    protected WorkflowStep() {}
    public UUID getId() { return id; }
    public WorkflowDefinition getWorkflow() { return workflow; } public void setWorkflow(WorkflowDefinition v) { workflow = v; }
    public int getStepOrder() { return stepOrder; } public void setStepOrder(int v) { stepOrder = v; }
    public IntegrationApp getTargetApp() { return targetApp; } public void setTargetApp(IntegrationApp v) { targetApp = v; }
    public Enums.HttpMethod getHttpMethod() { return httpMethod; } public void setHttpMethod(Enums.HttpMethod v) { httpMethod = v; }
    public String getEndpointPath() { return endpointPath; } public void setEndpointPath(String v) { endpointPath = v; }
    public Map<String, Object> getTransformSchema() { return transformSchema; } public void setTransformSchema(Map<String, Object> v) { transformSchema = v; }
    public Map<String, Object> getRetryConfig() { return retryConfig; } public void setRetryConfig(Map<String, Object> v) { retryConfig = v; }
    public Enums.FailureStrategy getFailureStrategy() { return failureStrategy; } public void setFailureStrategy(Enums.FailureStrategy v) { failureStrategy = v; }
}

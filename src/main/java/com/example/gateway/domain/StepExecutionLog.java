package com.example.gateway.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "step_execution_log", indexes = {
        @Index(name = "ix_step_log_execution", columnList = "execution_id"),
        @Index(name = "ix_step_log_step", columnList = "step_id"),
        @Index(name = "ix_step_log_status_code", columnList = "status_code")
})
public class StepExecutionLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "execution_id", nullable = false) private WorkflowExecution execution;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "step_id", nullable = false) private WorkflowStep step;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "request_payload", columnDefinition = "jsonb") private Map<String, Object> requestPayload;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "response_payload", columnDefinition = "jsonb") private Map<String, Object> responsePayload;
    @Column(name = "status_code") private Integer statusCode;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "execution_time_ms") private Long executionTimeMs;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    public StepExecutionLog() {}
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public UUID getId() { return id; } public WorkflowExecution getExecution() { return execution; } public void setExecution(WorkflowExecution v) { execution = v; }
    public WorkflowStep getStep() { return step; } public void setStep(WorkflowStep v) { step = v; }
    public Map<String, Object> getRequestPayload() { return requestPayload; } public void setRequestPayload(Map<String, Object> v) { requestPayload = v; }
    public Map<String, Object> getResponsePayload() { return responsePayload; } public void setResponsePayload(Map<String, Object> v) { responsePayload = v; }
    public Integer getStatusCode() { return statusCode; } public void setStatusCode(Integer v) { statusCode = v; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String v) { errorMessage = v; }
    public Long getExecutionTimeMs() { return executionTimeMs; } public void setExecutionTimeMs(Long v) { executionTimeMs = v; }
    public Instant getCreatedAt() { return createdAt; }
}

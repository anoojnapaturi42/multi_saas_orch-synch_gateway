package com.example.gateway.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "workflow_execution", indexes = {
        @Index(name = "ix_workflow_execution_workflow_started", columnList = "workflow_id, start_time"),
        @Index(name = "ix_workflow_execution_status", columnList = "status"),
        @Index(name = "ix_workflow_execution_execution_id", columnList = "execution_id", unique = true)
})
public class WorkflowExecution {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "workflow_id", nullable = false) private WorkflowDefinition workflow;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Enums.ExecutionStatus status = Enums.ExecutionStatus.PENDING;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "trigger_payload", columnDefinition = "jsonb", nullable = false) private Map<String, Object> triggerPayload = Map.of();
    @Column(name = "start_time") private Instant startTime;
    @Column(name = "end_time") private Instant endTime;
    protected WorkflowExecution() {}
    public UUID getExecutionId() { return executionId; }
    public WorkflowDefinition getWorkflow() { return workflow; } public void setWorkflow(WorkflowDefinition v) { workflow = v; }
    public Enums.ExecutionStatus getStatus() { return status; } public void setStatus(Enums.ExecutionStatus v) { status = v; }
    public Map<String, Object> getTriggerPayload() { return triggerPayload; } public void setTriggerPayload(Map<String, Object> v) { triggerPayload = v; }
    public Instant getStartTime() { return startTime; } public void setStartTime(Instant v) { startTime = v; }
    public Instant getEndTime() { return endTime; } public void setEndTime(Instant v) { endTime = v; }
}

package com.example.gateway.audit;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "ix_audit_logs_user_time", columnList = "user_id, event_timestamp"),
        @Index(name = "ix_audit_logs_action_time", columnList = "action, event_timestamp"),
        @Index(name = "ix_audit_logs_resource", columnList = "resource_target")
})
public class AuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false, length = 200) private String userId;
    @Column(nullable = false, length = 100) private String action;
    @Column(name = "resource_target", nullable = false, length = 500) private String resourceTarget;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(name = "event_timestamp", nullable = false, updatable = false) private Instant timestamp;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "old_state", columnDefinition = "jsonb") private Map<String, Object> oldState;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "new_state", columnDefinition = "jsonb") private Map<String, Object> newState;

    protected AuditLogEntity() {}

    public AuditLogEntity(String userId, String action, String resourceTarget, String ipAddress,
                          Map<String, Object> oldState, Map<String, Object> newState) {
        this.userId = userId; this.action = action; this.resourceTarget = resourceTarget;
        this.ipAddress = ipAddress; this.oldState = oldState; this.newState = newState;
        this.timestamp = Instant.now();
    }
    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getAction() { return action; }
    public String getResourceTarget() { return resourceTarget; }
    public String getIpAddress() { return ipAddress; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getOldState() { return oldState; }
    public Map<String, Object> getNewState() { return newState; }
}

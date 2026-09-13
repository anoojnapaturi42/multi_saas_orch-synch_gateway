package com.example.gateway.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "field_mapping_config", indexes = {
        @Index(name = "ix_mapping_step", columnList = "step_id"),
        @Index(name = "ix_mapping_source_path", columnList = "source_path"),
        @Index(name = "ix_mapping_target_path", columnList = "target_path")
}, uniqueConstraints = @UniqueConstraint(name = "uk_mapping_step_paths", columnNames = {"step_id", "source_path", "target_path"}))
public class FieldMappingConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "step_id", nullable = false) private WorkflowStep step;
    @Column(name = "source_path", nullable = false, length = 1000) private String sourcePath;
    @Column(name = "target_path", nullable = false, length = 1000) private String targetPath;
    @Column(name = "transform_type", length = 80) private String transformType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "transform_config", columnDefinition = "jsonb", nullable = false) private Map<String, Object> transformConfig = Map.of();
    @Column(name = "is_enabled", nullable = false) private boolean enabled = true;
    protected FieldMappingConfig() {}
    public UUID getId() { return id; } public WorkflowStep getStep() { return step; } public void setStep(WorkflowStep v) { step = v; }
    public String getSourcePath() { return sourcePath; } public void setSourcePath(String v) { sourcePath = v; }
    public String getTargetPath() { return targetPath; } public void setTargetPath(String v) { targetPath = v; }
    public String getTransformType() { return transformType; } public void setTransformType(String v) { transformType = v; }
    public Map<String, Object> getTransformConfig() { return transformConfig; } public void setTransformConfig(Map<String, Object> v) { transformConfig = v; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled = v; }
}

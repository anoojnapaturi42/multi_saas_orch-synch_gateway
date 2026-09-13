package com.example.gateway.repository;

import com.example.gateway.domain.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {
    List<WorkflowDefinition> findAllBySourceAppIdAndEnabledTrue(UUID sourceAppId);
}

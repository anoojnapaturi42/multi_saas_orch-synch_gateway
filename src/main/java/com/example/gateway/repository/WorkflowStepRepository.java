package com.example.gateway.repository;

import com.example.gateway.domain.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, UUID> {
    List<WorkflowStep> findAllByWorkflowIdOrderByStepOrder(UUID workflowId);
}

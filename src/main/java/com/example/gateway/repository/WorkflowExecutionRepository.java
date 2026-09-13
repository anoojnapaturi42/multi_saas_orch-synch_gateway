package com.example.gateway.repository;

import com.example.gateway.domain.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, UUID> {
    Optional<WorkflowExecution> findByExecutionId(UUID executionId);
}

package com.example.gateway.admin;

import com.example.gateway.audit.AuditLog;
import com.example.gateway.config.RabbitMqTopologyConfig;
import com.example.gateway.messaging.IngestionEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Entry point for protected administrative operations. */
@Service
public class WorkflowAdminService {
    private final RabbitTemplate rabbitTemplate;

    public WorkflowAdminService(RabbitTemplate rabbitTemplate) { this.rabbitTemplate = rabbitTemplate; }

    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "WORKFLOW_MODIFICATION", resourceTarget = "#workflowId", oldState = "#oldState", newState = "#newState")
    public void modifyWorkflow(UUID workflowId, JsonNode oldState, JsonNode newState) {
        // Persist the configuration mutation here or delegate to a workflow repository.
    }

    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(action = "SCHEMA_UPDATE", resourceTarget = "#stepId", oldState = "#oldState", newState = "#newState")
    public void updateSchema(UUID stepId, JsonNode oldState, JsonNode newState) {
        // Persist the step schema mutation here or delegate to a workflow repository.
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @AuditLog(action = "MANUAL_REDRIVE", resourceTarget = "#executionId")
    public void redrive(UUID executionId, UUID sourceAppId, JsonNode payload) {
        rabbitTemplate.convertAndSend(RabbitMqTopologyConfig.INGESTION_EXCHANGE,
                RabbitMqTopologyConfig.EVENTS_KEY,
                new IngestionEvent(executionId, sourceAppId, payload, Instant.now()));
    }
}

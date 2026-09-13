package com.example.gateway.messaging;

import com.example.gateway.config.RabbitMqTopologyConfig;
import com.example.gateway.domain.Enums;
import com.example.gateway.domain.StepExecutionLog;
import com.example.gateway.domain.WorkflowDefinition;
import com.example.gateway.domain.WorkflowExecution;
import com.example.gateway.domain.WorkflowStep;
import com.example.gateway.execution.GenericApiExecutorService;
import com.example.gateway.execution.StepExecutionResult;
import com.example.gateway.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class WorkflowOrchestratorListener {
    private static final int MAX_RETRIES = 4;
    private final WorkflowDefinitionRepository workflowRepository;
    private final WorkflowStepRepository stepRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final StepExecutionLogRepository logRepository;
    private final GenericApiExecutorService executor;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowOrchestratorListener(WorkflowDefinitionRepository workflowRepository,
                                        WorkflowStepRepository stepRepository,
                                        WorkflowExecutionRepository executionRepository,
                                        StepExecutionLogRepository logRepository,
                                        GenericApiExecutorService executor,
                                        RabbitTemplate rabbitTemplate,
                                        ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository; this.stepRepository = stepRepository;
        this.executionRepository = executionRepository; this.logRepository = logRepository;
        this.executor = executor; this.rabbitTemplate = rabbitTemplate; this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqTopologyConfig.EVENTS_QUEUE)
    @Transactional
    public void process(IngestionEvent event, Message message,
                        @Header(name = "x-retry-count", required = false, defaultValue = "0") int retryCount) {
        var workflows = workflowRepository.findAllBySourceAppIdAndEnabledTrue(event.sourceAppId());
        if (workflows.isEmpty()) {
            publishFailure(event, null, null, retryCount, 0, "NO_WORKFLOW", "No enabled workflow for source app");
            return;
        }
        for (WorkflowDefinition workflow : workflows) {
            WorkflowExecution execution = executionRepository.findByExecutionId(event.executionId())
                    .orElseGet(() -> new WorkflowExecution(event.executionId()));
            execution.setWorkflow(workflow); execution.setTriggerPayload(asMap(event.payload()));
            execution.setStatus(Enums.ExecutionStatus.RUNNING); execution.setStartTime(Instant.now());
            executionRepository.save(execution);
            var steps = stepRepository.findAllByWorkflowIdOrderByStepOrder(workflow.getId());
            for (int index = 0; index < steps.size();) {
                WorkflowStep first = steps.get(index);
                String parallelGroup = String.valueOf(first.getTransformSchema().getOrDefault("parallel_group", ""));
                if (!parallelGroup.isBlank()) {
                    java.util.List<WorkflowStep> group = new java.util.ArrayList<>();
                    while (index < steps.size() && parallelGroup.equals(String.valueOf(steps.get(index).getTransformSchema().getOrDefault("parallel_group", "")))) group.add(steps.get(index++));
                    var results = Flux.fromIterable(group).flatMapSequential(step -> executor.execute(step, event.payload())).collectList().block();
                    for (int position = 0; position < group.size(); position++) {
                        StepExecutionResult result = results == null ? null : results.get(position);
                        logRepository.save(log(execution, group.get(position), event.payload(), result));
                        if (result == null || !result.successful()) { handleFailure(event, workflow, execution, group.get(position), result, retryCount); return; }
                    }
                } else {
                    index++;
                    StepExecutionResult result = executor.execute(first, event.payload()).block();
                    logRepository.save(log(execution, first, event.payload(), result));
                    if (result == null || !result.successful()) { handleFailure(event, workflow, execution, first, result, retryCount); return; }
                }
            }
            execution.setStatus(Enums.ExecutionStatus.COMPLETED); execution.setEndTime(Instant.now());
            executionRepository.save(execution);
        }
    }

    private void handleFailure(IngestionEvent event, WorkflowDefinition workflow, WorkflowExecution execution,
                               WorkflowStep step, StepExecutionResult result, int retryCount) {
        int status = result == null ? 0 : result.httpStatus();
        boolean transientFailure = status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        if (transientFailure && retryCount < MAX_RETRIES) {
            execution.setStatus(Enums.ExecutionStatus.RETRYING); execution.setEndTime(Instant.now()); executionRepository.save(execution);
            var retryMessage = rabbitTemplate.getMessageConverter().toMessage(event,
                    new org.springframework.amqp.core.MessageProperties());
            retryMessage.getMessageProperties().setHeader("x-retry-count", retryCount + 1);
            retryMessage.getMessageProperties().setExpiration(Long.toString(Duration.ofSeconds(2L << retryCount).toMillis()));
            rabbitTemplate.send(RabbitMqTopologyConfig.RETRY_EXCHANGE, RabbitMqTopologyConfig.RETRY_KEY, retryMessage);
        } else {
            execution.setStatus(Enums.ExecutionStatus.FAILED); execution.setEndTime(Instant.now()); executionRepository.save(execution);
            publishFailure(event, workflow, step, retryCount, status,
                    transientFailure ? "RETRY_EXHAUSTED" : "PERMANENT_FAILURE",
                    result == null ? "Step execution returned no result" : result.errorMessage());
        }
    }

    private StepExecutionLog log(WorkflowExecution execution, WorkflowStep step, JsonNode payload, StepExecutionResult result) {
        StepExecutionLog log = new StepExecutionLog();
        log.setExecution(execution); log.setStep(step); log.setRequestPayload(asMap(payload));
        log.setStatusCode(result == null ? null : result.httpStatus()); log.setExecutionTimeMs(result == null ? null : result.duration().toMillis());
        log.setErrorMessage(result == null ? "No result" : result.errorMessage());
        if (result != null && result.rawResponse() != null) {
            try { log.setResponsePayload(asMap(objectMapper.readTree(result.rawResponse()))); }
            catch (Exception ignored) { log.setErrorMessage(result.rawResponse()); }
        }
        return log;
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> asMap(JsonNode node) {
        if (node == null || node.isNull()) return java.util.Map.of();
        return node.isObject() ? objectMapper.convertValue(node, java.util.Map.class) : java.util.Map.of("value", node);
    }

    private void publishFailure(IngestionEvent event, WorkflowDefinition workflow, WorkflowStep step,
                                int retryCount, int status, String type, String error) {
        rabbitTemplate.convertAndSend(RabbitMqTopologyConfig.DLQ_EXCHANGE, RabbitMqTopologyConfig.DLQ_KEY,
                new WorkflowFailureMessage(event.executionId(), workflow == null ? null : workflow.getId(), event.sourceAppId(),
                        step == null ? null : step.getId(), status, retryCount, type, error, Instant.now()));
    }
}

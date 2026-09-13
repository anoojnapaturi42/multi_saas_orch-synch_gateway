package com.example.gateway;

import com.example.gateway.config.RabbitMqTopologyConfig;
import com.example.gateway.domain.WorkflowStep;
import com.example.gateway.execution.GenericApiExecutorService;
import com.example.gateway.execution.StepExecutionResult;
import com.example.gateway.messaging.IngestionEvent;
import com.example.gateway.messaging.WorkflowOrchestratorListener;
import com.example.gateway.repository.StepExecutionLogRepository;
import com.example.gateway.repository.WorkflowDefinitionRepository;
import com.example.gateway.repository.WorkflowExecutionRepository;
import com.example.gateway.repository.WorkflowStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = "/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ConfigurationDrivenScenariosIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean GenericApiExecutorService executor;
    @MockBean RabbitTemplate rabbitTemplate;
    @MockBean JwtDecoder jwtDecoder;

    @jakarta.annotation.Resource WorkflowDefinitionRepository workflowRepository;
    @jakarta.annotation.Resource WorkflowStepRepository stepRepository;
    @jakarta.annotation.Resource WorkflowExecutionRepository executionRepository;
    @jakarta.annotation.Resource StepExecutionLogRepository logRepository;
    @jakarta.annotation.Resource WorkflowOrchestratorListener listener;
    @jakarta.annotation.Resource ObjectMapper objectMapper;

    @Test
    void employeeOnboardingExecutesConfiguredStepsInOrder() {
        var workflow = workflow("Automated Employee Onboarding");
        var steps = steps(workflow.getId());
        configureSuccessfulExecutor();

        listener.process(event("10000000-0000-0000-0000-000000000001", "employee"), null, 0);

        assertThat(steps).extracting(WorkflowStep::getStepOrder).containsExactly(1, 2, 3);
        assertThat(steps).extracting(step -> step.getTargetApp().getName())
                .containsExactly("Internal DB API", "Workspace REST API", "Slack Webhook");
        assertThat(executionRepository.findAll()).anyMatch(e -> e.getStatus().name().equals("COMPLETED"));
        verify(executor, times(3)).execute(any(), any());
    }

    @Test
    void highPriorityEscalationSendsPermanentPagerDutyFailureToDlq() {
        var workflow = workflow("High-Priority Escalation");
        when(executor.execute(any(), any())).thenAnswer(invocation -> {
            WorkflowStep step = invocation.getArgument(0);
            return Mono.just(step.getTargetApp().getName().equals("PagerDuty")
                    ? StepExecutionResult.failure("{\"error\":\"unauthorized\"}", 401, Duration.ofMillis(20), "application/json", "PagerDuty rejected the event")
                    : StepExecutionResult.success("{}", 200, Duration.ofMillis(20), "application/json"));
        });

        listener.process(event("10000000-0000-0000-0000-000000000005", "alert"), null, 0);

        assertThat(steps(workflow.getId())).extracting(WorkflowStep::getStepOrder).containsExactly(1, 2, 3, 4);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMqTopologyConfig.DLQ_EXCHANGE), eq(RabbitMqTopologyConfig.DLQ_KEY), any());
        verify(executor, times(4)).execute(any(), any());
    }

    @Test
    void procurementUsesConfiguredSoapThenJsonNormalizationSteps() {
        var workflow = workflow("Procurement & SaaS Licensing");
        var steps = steps(workflow.getId());

        assertThat(steps).extracting(step -> step.getTransformSchema().get("_requestFormat"))
                .containsExactly("SOAP", "JSON");
        assertThat(steps.get(1).getTransformSchema()).containsEntry("invoiceId", "XML_TO_JSON($.PurchaseOrderResponse.InvoiceId)");
        assertThat(steps.get(0).getRetryConfig()).containsEntry("backoffMs", 4000);
    }

    @Test
    void offboardingKeepsThreeFanoutTargetsAndPersistsAsyncCompletion() {
        var workflow = workflow("Cross-Departmental Offboarding");
        configureSuccessfulExecutor();
        UUID executionId = UUID.randomUUID();

        listener.process(new IngestionEvent(executionId, UUID.fromString("10000000-0000-0000-0000-000000000015"), payload("deletion"), Instant.now()), null, 0);

        var steps = steps(workflow.getId());
        assertThat(steps).allMatch(step -> "offboarding_fanout".equals(step.getTransformSchema().get("parallel_group")));
        assertThat(steps).extracting(step -> step.getTargetApp().getName())
                .containsExactly("CRM", "Payment Gateway", "Storage Buckets");
        assertThat(executionRepository.findByExecutionId(executionId)).get().extracting(e -> e.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(logRepository.findAll()).hasSizeGreaterThanOrEqualTo(3);
    }

    private void configureSuccessfulExecutor() {
        when(executor.execute(any(), any())).thenReturn(Mono.just(StepExecutionResult.success("{}", 200, Duration.ofMillis(10), "application/json")));
    }

    private com.example.gateway.domain.WorkflowDefinition workflow(String name) {
        return workflowRepository.findAll().stream().filter(w -> w.getName().equals(name)).findFirst().orElseThrow();
    }

    private java.util.List<WorkflowStep> steps(UUID workflowId) {
        return stepRepository.findAllByWorkflowIdOrderByStepOrder(workflowId);
    }

    private IngestionEvent event(String sourceAppId, String key) {
        return new IngestionEvent(UUID.randomUUID(), UUID.fromString(sourceAppId), payload(key), Instant.now());
    }

    private ObjectNode payload(String key) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("event", key); payload.putObject("worker").put("id", "E-100").put("work_email", "mira.chen@northstar.io").put("first_name", "Mira").put("last_name", "Chen");
        payload.putObject("alert").put("id", "DD-100").put("title", "production alert").put("service", "payments");
        return payload;
    }
}

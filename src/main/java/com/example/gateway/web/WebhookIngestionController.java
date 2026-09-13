package com.example.gateway.web;

import com.example.gateway.config.RabbitMqTopologyConfig;
import com.example.gateway.messaging.IngestionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookIngestionController {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public WebhookIngestionController(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate; this.objectMapper = objectMapper;
    }

    @PostMapping("/ingest/{sourceAppId}")
    public ResponseEntity<IngestionAcceptedResponse> ingest(@PathVariable UUID sourceAppId,
                                                              @RequestBody(required = false) String rawPayload) {
        UUID executionId = UUID.randomUUID();
        JsonNode payload = parsePayload(rawPayload);
        rabbitTemplate.convertAndSend(RabbitMqTopologyConfig.INGESTION_EXCHANGE,
                RabbitMqTopologyConfig.EVENTS_KEY,
                new IngestionEvent(executionId, sourceAppId, payload, Instant.now()));
        return ResponseEntity.accepted().body(new IngestionAcceptedResponse(executionId, "ACCEPTED"));
    }

    private JsonNode parsePayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) return objectMapper.getNodeFactory().nullNode();
        try { return objectMapper.readTree(rawPayload); }
        catch (Exception ignored) { return TextNode.valueOf(rawPayload); }
    }

    public record IngestionAcceptedResponse(UUID executionId, String status) {}
}

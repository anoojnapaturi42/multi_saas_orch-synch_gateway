package com.example.gateway.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record IngestionEvent(UUID executionId, UUID sourceAppId, JsonNode payload, Instant receivedAt) {}

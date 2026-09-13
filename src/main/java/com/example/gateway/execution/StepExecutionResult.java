package com.example.gateway.execution;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;

/** Stable result contract emitted by every outbound workflow step. */
public record StepExecutionResult(
        String rawResponse,
        int httpStatus,
        Duration duration,
        boolean successful,
        String contentType,
        String errorMessage) {
    public static StepExecutionResult success(String body, int status, Duration duration, String contentType) {
        return new StepExecutionResult(body, status, duration, status >= 200 && status < 300, contentType, null);
    }

    public static StepExecutionResult failure(String body, int status, Duration duration, String contentType, String error) {
        return new StepExecutionResult(body, status, duration, false, contentType, error);
    }
}

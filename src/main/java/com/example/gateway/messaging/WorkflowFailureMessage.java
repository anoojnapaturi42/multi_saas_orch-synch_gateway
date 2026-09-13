package com.example.gateway.messaging;

import java.time.Instant;
import java.util.UUID;

public record WorkflowFailureMessage(
        UUID executionId,
        UUID workflowId,
        UUID sourceAppId,
        UUID stepId,
        int httpStatus,
        int retryCount,
        String failureType,
        String errorMessage,
        Instant failedAt) {}

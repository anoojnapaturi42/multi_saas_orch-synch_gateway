package com.example.gateway.domain;

public final class Enums {
    private Enums() {}

    public enum AuthType { OAUTH2, API_KEY, BASIC }
    public enum TriggerType { WEBHOOK, POLLING, SCHEDULED }
    public enum HttpMethod { GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS }
    public enum FailureStrategy { ABORT, CONTINUE, DLQ }
    public enum ExecutionStatus { PENDING, RUNNING, COMPLETED, FAILED, RETRYING }
}

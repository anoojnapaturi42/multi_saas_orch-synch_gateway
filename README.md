# Enterprise Multi-SaaS Gateway persistence model

This project contains a Java 21 / Spring Data JPA persistence model for a configuration-driven orchestration gateway.

## Configuration-driven execution

An execution engine can load a `WorkflowDefinition`, order its `WorkflowStep` rows, resolve the `IntegrationApp` for each step, and interpret `transformSchema`, `retryConfig`, and `FieldMappingConfig` without a Java class per integration or workflow. `FieldMappingConfig` supports mappings such as:

```text
$.employee.email -> $.user.primary_email
```

The JSON columns are mapped with Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)` and are backed by PostgreSQL `jsonb` columns. The migration is at `src/main/resources/db/migration/V1__enterprise_multi_saas_gateway.sql` and is ready for Flyway.

## Dynamic transformation service

`DynamicDataTransformerService` is at `src/main/java/com/example/gateway/transform/DynamicDataTransformerService.java` and accepts a Jackson `JsonNode` input plus a database-defined schema:

```json
{
  "email": "$.user.work_email",
  "source": "CONST:WORKDAY",
  "fullName": "CONCAT($.first_name, ' ', $.last_name)",
  "isVip": "EQUALS($.account.tier, 'ENTERPRISE')"
}
```

For XML-to-JSON rules, pass the XML as a textual `JsonNode` and set `"_inputFormat": "XML"`. The service converts it with a securely configured Jackson `XmlMapper`, then applies the same JsonPath mappings. `XML_TO_JSON($.employee.email)` is also supported as an explicit, self-documenting alias. Missing paths, malformed XML/JSON, invalid JsonPath expressions, and malformed function arguments are captured under `_transformationErrors`; successful fields are still returned.

## Outbound API execution

`GenericApiExecutorService` executes each `WorkflowStep` with WebClient. Request behavior is configured in the step/app records:

- `IntegrationApp.authConfig`: API key `{ "headerName": "X-API-Key", "value": "..." }`, Basic `{ "username": "...", "password": "..." }`, or OAuth2 `{ "accessToken": "..." }` / token endpoint settings.
- `IntegrationApp.rateLimitConfig`: `{ "capacity": 100, "refillTokens": 100, "refillPeriodMillis": 60000 }`.
- `WorkflowStep.transformSchema._requestFormat`: `JSON`, `XML`, or `SOAP`; SOAP may also set `_soapAction`.
- `WorkflowStep.retryConfig`: `{ "maxAttempts": 3, "backoffMs": 250 }`.

The Redis token bucket uses `gateway:rate-limit:{targetAppId}` keys, so multiple gateway instances share vendor quotas. Resilience4j retries HTTP 429 and 5xx responses using the step’s retry configuration. The result is returned as `StepExecutionResult` with raw response text, HTTP status, content type, duration, and error information.

## Event ingestion

`POST /api/v1/webhooks/ingest/{sourceAppId}` accepts arbitrary request text, parses valid JSON, preserves XML/non-JSON content as text, publishes an `IngestionEvent` to `ingestion.exchange`, and responds with HTTP `202` and the generated `executionId`. `WorkflowOrchestratorListener` resolves all enabled workflows whose `source_app_id` matches, then executes their steps sequentially. Transient HTTP failures are retried through `workflow.retry.queue` with 2s, 4s, 8s, and 16s delays; HTTP 400/401/403 and exhausted retries are published to `workflow.dlq` with execution, workflow, step, status, retry count, and error metadata.

## Design notes

- UUID identifiers avoid coordination between tenants and gateway instances.
- Relationships are `LAZY` to prevent loading entire workflow graphs during execution-log queries.
- Enum values are stored as strings for readable, migration-friendly data.
- Composite indexes cover tenant lookup, workflow step ordering, and execution history.
- GIN `jsonb_path_ops` indexes support containment/path-oriented configuration and payload searches.
- Tenant ownership is explicit on workflows and apps; application services should enforce that referenced apps belong to the same tenant.

# Switchboard — Enterprise Multi-SaaS Orchestration Gateway

Switchboard is a configuration-driven integration gateway for connecting SaaS systems without creating a Java implementation for every workflow or vendor. Workflow definitions, ordered steps, JSON transformations, authentication settings, retry policies, rate limits, and failure strategies are persisted as database records.

The package contains:

- Java 21 / Spring Boot backend using Spring Data JPA, PostgreSQL JSONB, WebClient, RabbitMQ, Redis, Resilience4j, OAuth2 Resource Server, and AOP audit logging.
- React + TypeScript / Vite / Tailwind administrative UI for monitoring executions and visually building workflow steps.
- PostgreSQL/Flyway migrations and a complete configuration fixture for four business scenarios.
- A Testcontainers integration suite that proves workflow behavior is resolved from seeded configuration.

## Architecture

```text
Webhook / operator action
        │
        ▼
POST /api/v1/webhooks/ingest/{sourceAppId}
        │  publish immediately, return 202 + executionId
        ▼
RabbitMQ ingestion.exchange
        │
        ▼
workflow.events.queue ──► WorkflowOrchestratorListener
                              │
                              ├─ load enabled workflow and ordered steps from PostgreSQL
                              ├─ transform JsonNode payload from database rules
                              ├─ acquire Redis token-bucket permit per target app
                              ├─ call REST, XML, or SOAP endpoint with dynamic auth
                              ├─ retry transient failures through workflow.retry.queue
                              └─ persist execution/step logs or publish workflow.dlq
```

## Prerequisites

- Java 21+, Maven 3.9+, Node.js 20+ and npm
- PostgreSQL 15+ with `pgcrypto`, RabbitMQ, and Redis
- Docker Desktop for integration tests
- An OIDC issuer such as Keycloak or Okta for non-test authentication

## Start the backend

Configure PostgreSQL, RabbitMQ, Redis, and the JWT issuer through environment variables or `application.yml`:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/gateway"
$env:SPRING_DATASOURCE_USERNAME = "gateway"
$env:SPRING_DATASOURCE_PASSWORD = "gateway"
$env:SPRING_RABBITMQ_HOST = "localhost"
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:JWT_ISSUER_URI = "http://localhost:8081/realms/gateway"
mvn spring-boot:run
```

Flyway applies `src/main/resources/db/migration/V1__enterprise_multi_saas_gateway.sql` on startup. The migration creates the integration, workflow, execution, step-log, field-mapping, and audit tables plus constraints and indexes.

## Start the admin UI

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` requests to `http://localhost:8080`.

The UI contains two core pages:

1. **Execution Monitor** — live-polling execution table, status metrics, filters, expandable request/response traces, and failed-execution re-drive.
2. **Workflow Builder** — ordered step editor, draggable JsonPath field mapper, target schema selectors, live transformation preview, and retry strategy form.

The UI uses demo data if execution endpoints are unavailable, making the interaction model explorable before query endpoints are connected.

## Ingestion and workflow execution

Submit an arbitrary JSON, XML, or text webhook payload:

```http
POST /api/v1/webhooks/ingest/{sourceAppId}
Content-Type: application/json

{
  "worker": {
    "id": "E-100",
    "work_email": "mira.chen@northstar.io"
  }
}
```

The controller publishes an `IngestionEvent` and returns:

```json
{ "executionId": "...", "status": "ACCEPTED" }
```

The listener selects enabled workflows whose `source_app_id` matches and executes their steps in `step_order`. Steps with the same `transform_schema.parallel_group` execute concurrently as a fan-out group.

RabbitMQ topology:

| Resource | Purpose |
|---|---|
| `ingestion.exchange` | Main event exchange |
| `workflow.events.queue` | Background workflow processing |
| `workflow.retry.exchange` / `workflow.retry.queue` | Per-message delayed retries |
| `workflow.dlq.exchange` / `workflow.dlq` | Permanent and exhausted failures |

Transient responses (`429`, `500`, `502`, `503`, `504`) use configured retry policy and exponential delays. Permanent responses such as `400`, `401`, and `403` are published directly to the DLQ with execution, workflow, step, status, retry count, and error metadata.

## Configuration-driven transformations

`DynamicDataTransformerService` supports rules such as:

```json
{
  "email": "$.worker.work_email",
  "source": "CONST:WORKDAY",
  "displayName": "CONCAT($.worker.first_name, ' ', $.worker.last_name)",
  "isEnterprise": "EQUALS($.account.tier, 'ENTERPRISE')"
}
```

For XML input, use `"_inputFormat": "XML"`; `XML_TO_JSON($.PurchaseOrderResponse.InvoiceId)` is also supported. Missing paths, malformed documents, invalid JsonPath expressions, and malformed function arguments are returned as structured `_transformationErrors` without taking down the pipeline.

## Dynamic outbound calls

`IntegrationApp` records hold vendor base URLs, auth types, default headers, auth configuration, and rate-limit configuration. `WorkflowStep` records hold HTTP method, endpoint path, transformation schema, retry configuration, and failure strategy.

```json
{
  "authConfig": { "headerName": "X-API-Key", "value": "secret-reference" },
  "rateLimitConfig": {
    "capacity": 100,
    "refillTokens": 100,
    "refillPeriodMillis": 60000
  }
}
```

Use `_requestFormat: "JSON"`, `"XML"`, or `"SOAP"` in the step transform schema. SOAP steps may provide `_soapAction`. The Redis token bucket is keyed by `gateway:rate-limit:{targetAppId}`, allowing multiple gateway instances to share vendor quotas. In production, store secret references rather than raw credentials in JSONB and resolve them through a secret manager.

## Security and audit

The backend is an OAuth2 Resource Server. Set `JWT_ISSUER_URI` to a Keycloak, Okta, or custom OIDC issuer. JWT `roles`, `groups`, and Keycloak `realm_access.roles` claims are normalized into Spring authorities.

| Role | Access |
|---|---|
| `ROLE_ADMIN` | Workflow/schema configuration and manual re-drive |
| `ROLE_OPERATOR` | Manual re-drive and audit-log viewing |
| `ROLE_AUDITOR` | Read-only audit-log viewing |

Methods annotated with `@AuditLog` are intercepted by `AuditLoggingAspect`. Each audit row stores the authenticated user ID, action, resource target, client IP, timestamp, and old/new JSON state in `audit_logs`. Audit writes use `REQUIRES_NEW` transaction propagation so an administrative failure does not erase its audit record.

## Tested use cases

`src/test/resources/data.sql` seeds four workflows using the same generic entities and listener. `src/test/java/com/example/gateway/ConfigurationDrivenScenariosIT.java` runs them against PostgreSQL in Testcontainers.

| Use case | Database-defined pipeline | What it validates |
|---|---|---|
| Automated Employee Onboarding | Workday webhook → Internal DB API → Workspace REST API → Slack webhook | Ordered steps, JSON mappings, per-app auth/rate limits, retry settings, and persisted execution/logs |
| High-Priority Escalation | Datadog alert → deployment metadata → Jira ticket → Slack incident channel → PagerDuty | Multi-step escalation and direct DLQ routing for a permanent PagerDuty `401` failure |
| Procurement & SaaS Licensing | Approval webhook → vendor SOAP API → XML/JSON normalization → accounting API | Mixed SOAP/JSON modes and XML-to-JSON rules without provider-specific Java code |
| Cross-Departmental Offboarding | Deletion event → CRM, payment gateway, and storage bucket fan-out | Shared `parallel_group` configuration, concurrent outbound calls, and async Postgres tracking |

The architectural meaning is that adding or changing a workflow is an `INSERT` or `UPDATE` to `integration_app`, `workflow_definition`, `workflow_step`, or `field_mapping_config`; the orchestrator does not branch on scenario names or vendor names.

## Run integration tests

```powershell
mvn verify
```

The suite starts PostgreSQL 16 through Testcontainers, applies Flyway, loads `data.sql`, mocks external HTTP/RabbitMQ boundaries, and verifies configuration plus execution outcomes. Docker must be running. Tests never call real SaaS systems.

## Project layout

```text
src/main/java/com/example/gateway/
├── admin/          RBAC-protected admin and audit query services
├── audit/          @AuditLog, aspect, entity, and repository
├── config/         RabbitMQ topology
├── domain/         JPA entities and enums
├── execution/      WebClient, auth, Redis rate limiter, result contract
├── messaging/      ingestion events, listener, and DLQ messages
├── repository/     Spring Data repositories
├── security/       OAuth2 JWT resource-server configuration
├── transform/      JsonPath/Jackson transformation service
└── web/            webhook ingestion controller
frontend/src/
├── App.tsx         execution monitor and navigation shell
├── Configurator.tsx workflow builder page
├── api.ts          TanStack Query API functions and demo fallback
└── styles.css      Tailwind/CSS visual system
```

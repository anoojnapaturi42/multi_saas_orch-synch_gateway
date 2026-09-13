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

## Design notes

- UUID identifiers avoid coordination between tenants and gateway instances.
- Relationships are `LAZY` to prevent loading entire workflow graphs during execution-log queries.
- Enum values are stored as strings for readable, migration-friendly data.
- Composite indexes cover tenant lookup, workflow step ordering, and execution history.
- GIN `jsonb_path_ops` indexes support containment/path-oriented configuration and payload searches.
- Tenant ownership is explicit on workflows and apps; application services should enforce that referenced apps belong to the same tenant.

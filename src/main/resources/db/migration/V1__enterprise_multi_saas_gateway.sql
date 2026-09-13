CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE integration_app (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(100) NOT NULL,
    name varchar(120) NOT NULL,
    base_url varchar(2048) NOT NULL,
    auth_type varchar(20) NOT NULL CHECK (auth_type IN ('OAUTH2', 'API_KEY', 'BASIC')),
    default_headers jsonb NOT NULL DEFAULT '{}'::jsonb,
    auth_config jsonb NOT NULL DEFAULT '{}'::jsonb,
    rate_limit_config jsonb NOT NULL DEFAULT '{}'::jsonb,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_integration_app_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_integration_app_headers_object CHECK (jsonb_typeof(default_headers) = 'object'),
    CONSTRAINT ck_integration_app_auth_object CHECK (jsonb_typeof(auth_config) = 'object'),
    CONSTRAINT ck_integration_app_rate_limit_object CHECK (jsonb_typeof(rate_limit_config) = 'object')
);

CREATE TABLE workflow_definition (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(150) NOT NULL,
    trigger_type varchar(20) NOT NULL CHECK (trigger_type IN ('WEBHOOK', 'POLLING', 'SCHEDULED')),
    is_enabled boolean NOT NULL DEFAULT true,
    tenant_id varchar(100) NOT NULL,
    source_app_id uuid NOT NULL REFERENCES integration_app(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_workflow_definition_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE workflow_step (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id uuid NOT NULL REFERENCES workflow_definition(id) ON DELETE CASCADE,
    step_order integer NOT NULL CHECK (step_order > 0),
    target_app_id uuid NOT NULL REFERENCES integration_app(id) ON DELETE RESTRICT,
    http_method varchar(10) NOT NULL CHECK (http_method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')),
    endpoint_path varchar(2048) NOT NULL,
    transform_schema jsonb NOT NULL DEFAULT '{}'::jsonb,
    retry_config jsonb NOT NULL DEFAULT '{}'::jsonb,
    failure_strategy varchar(10) NOT NULL DEFAULT 'ABORT' CHECK (failure_strategy IN ('ABORT', 'CONTINUE', 'DLQ')),
    CONSTRAINT uk_workflow_step_order UNIQUE (workflow_id, step_order),
    CONSTRAINT ck_workflow_step_transform_object CHECK (jsonb_typeof(transform_schema) = 'object'),
    CONSTRAINT ck_workflow_step_retry_object CHECK (jsonb_typeof(retry_config) = 'object')
);

CREATE TABLE workflow_execution (
    execution_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id uuid NOT NULL REFERENCES workflow_definition(id) ON DELETE RESTRICT,
    status varchar(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'RETRYING')),
    trigger_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    start_time timestamptz,
    end_time timestamptz,
    CONSTRAINT ck_workflow_execution_payload_object CHECK (jsonb_typeof(trigger_payload) = 'object'),
    CONSTRAINT ck_workflow_execution_time_order CHECK (end_time IS NULL OR start_time IS NULL OR end_time >= start_time)
);

CREATE TABLE step_execution_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id uuid NOT NULL REFERENCES workflow_execution(execution_id) ON DELETE CASCADE,
    step_id uuid NOT NULL REFERENCES workflow_step(id) ON DELETE RESTRICT,
    request_payload jsonb,
    response_payload jsonb,
    status_code integer CHECK (status_code IS NULL OR status_code BETWEEN 100 AND 599),
    error_message text,
    execution_time_ms bigint CHECK (execution_time_ms IS NULL OR execution_time_ms >= 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE field_mapping_config (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    step_id uuid NOT NULL REFERENCES workflow_step(id) ON DELETE CASCADE,
    source_path varchar(1000) NOT NULL,
    target_path varchar(1000) NOT NULL,
    transform_type varchar(80),
    transform_config jsonb NOT NULL DEFAULT '{}'::jsonb,
    is_enabled boolean NOT NULL DEFAULT true,
    CONSTRAINT uk_mapping_step_paths UNIQUE (step_id, source_path, target_path),
    CONSTRAINT ck_mapping_transform_config_object CHECK (jsonb_typeof(transform_config) = 'object')
);

CREATE INDEX ix_integration_app_tenant ON integration_app (tenant_id);
CREATE INDEX ix_integration_app_auth_type ON integration_app (auth_type);
CREATE INDEX ix_integration_app_default_headers_gin ON integration_app USING gin (default_headers jsonb_path_ops);
CREATE INDEX ix_integration_app_auth_config_gin ON integration_app USING gin (auth_config jsonb_path_ops);
CREATE INDEX ix_integration_app_rate_limit_config_gin ON integration_app USING gin (rate_limit_config jsonb_path_ops);

CREATE INDEX ix_workflow_definition_tenant_enabled ON workflow_definition (tenant_id, is_enabled);
CREATE INDEX ix_workflow_definition_trigger ON workflow_definition (trigger_type);
CREATE INDEX ix_workflow_definition_source_enabled ON workflow_definition (source_app_id, is_enabled);

CREATE INDEX ix_workflow_step_workflow_order ON workflow_step (workflow_id, step_order);
CREATE INDEX ix_workflow_step_target_app ON workflow_step (target_app_id);
CREATE INDEX ix_workflow_step_transform_schema_gin ON workflow_step USING gin (transform_schema jsonb_path_ops);
CREATE INDEX ix_workflow_step_retry_config_gin ON workflow_step USING gin (retry_config jsonb_path_ops);

CREATE INDEX ix_workflow_execution_workflow_started ON workflow_execution (workflow_id, start_time DESC);
CREATE INDEX ix_workflow_execution_status ON workflow_execution (status);
CREATE INDEX ix_workflow_execution_trigger_payload_gin ON workflow_execution USING gin (trigger_payload jsonb_path_ops);

CREATE INDEX ix_step_log_execution ON step_execution_log (execution_id);
CREATE INDEX ix_step_log_step ON step_execution_log (step_id);
CREATE INDEX ix_step_log_status_code ON step_execution_log (status_code);
CREATE INDEX ix_step_log_request_payload_gin ON step_execution_log USING gin (request_payload jsonb_path_ops);
CREATE INDEX ix_step_log_response_payload_gin ON step_execution_log USING gin (response_payload jsonb_path_ops);

CREATE INDEX ix_mapping_step ON field_mapping_config (step_id);
CREATE INDEX ix_mapping_source_path ON field_mapping_config (source_path);
CREATE INDEX ix_mapping_target_path ON field_mapping_config (target_path);
CREATE INDEX ix_mapping_transform_config_gin ON field_mapping_config USING gin (transform_config jsonb_path_ops);

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_integration_app_updated_at BEFORE UPDATE ON integration_app
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_workflow_definition_updated_at BEFORE UPDATE ON workflow_definition
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

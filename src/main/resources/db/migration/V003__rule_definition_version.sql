-- V003: rule_definition + rule_version + rule_execution_audit (design 8.8).
-- Moved BEFORE tier/benefit/point_ledger so their rule_version_id FKs resolve.

CREATE TABLE rule_definition (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    code varchar(100) NOT NULL,
    name varchar(200) NOT NULL,
    domain varchar(32) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_rule_definition_code UNIQUE (program_id, code)
);

CREATE TABLE rule_version (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    rule_definition_id uuid NOT NULL REFERENCES rule_definition(id),
    version_no integer NOT NULL,
    status varchar(32) NOT NULL,
    effective_from timestamptz,
    effective_to timestamptz,
    artifact_uri varchar(1000),
    checksum varchar(128),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by varchar(200),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_rule_version UNIQUE (rule_definition_id, version_no),
    CONSTRAINT ck_rule_version_status
        CHECK (status IN ('DRAFT','TESTING','PUBLISHED','RETIRED'))
);

-- At most one PUBLISHED version per rule definition.
CREATE UNIQUE INDEX uk_rule_version_published
ON rule_version(rule_definition_id)
WHERE status = 'PUBLISHED';

CREATE TABLE rule_execution_audit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    rule_definition_id uuid NOT NULL REFERENCES rule_definition(id),
    rule_version_id uuid NOT NULL REFERENCES rule_version(id),
    correlation_id varchar(200),
    input_snapshot jsonb NOT NULL,
    result_snapshot jsonb NOT NULL,
    status varchar(32) NOT NULL,
    execution_ms integer,
    created_at timestamptz NOT NULL DEFAULT now()
);

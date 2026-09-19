-- V004: member / identity / attribute / merge (design 8.3).

CREATE TABLE member (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    member_no varchar(100) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at timestamptz NOT NULL DEFAULT now(),
    merged_to_member_id uuid NULL REFERENCES member(id),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_member_program_no UNIQUE (program_id, member_no),
    CONSTRAINT uk_member_scope UNIQUE (tenant_id, program_id, id),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE','INACTIVE','MERGED'))
);
CREATE INDEX idx_member_program_status ON member(tenant_id, program_id, status);

CREATE TABLE member_identity (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    member_id uuid NOT NULL REFERENCES member(id),
    identity_type varchar(32) NOT NULL,
    identity_source varchar(64) NOT NULL,
    identity_value varchar(512) NOT NULL,
    normalized_value varchar(512) NOT NULL,
    verified boolean NOT NULL DEFAULT false,
    is_primary boolean NOT NULL DEFAULT false,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    effective_from timestamptz NOT NULL DEFAULT now(),
    effective_to timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_member_identity_status CHECK (status IN ('ACTIVE','REVOKED'))
);
-- Active identity uniqueness within a program (design 4.6 / 9.2).
CREATE UNIQUE INDEX uk_member_identity_active
ON member_identity (program_id, identity_type, identity_source, normalized_value)
WHERE status = 'ACTIVE';
CREATE INDEX idx_identity_member
ON member_identity(tenant_id, program_id, member_id);

CREATE TABLE member_attribute_definition (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    code varchar(100) NOT NULL,
    name varchar(200) NOT NULL,
    data_type varchar(32) NOT NULL,
    required boolean NOT NULL DEFAULT false,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_member_attr_def UNIQUE (program_id, code)
);

CREATE TABLE member_attribute_value (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    member_id uuid NOT NULL REFERENCES member(id),
    definition_id uuid NOT NULL REFERENCES member_attribute_definition(id),
    value_string text,
    value_number numeric,
    value_boolean boolean,
    value_date date,
    value_datetime timestamptz,
    value_json jsonb,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_member_attr_value UNIQUE (member_id, definition_id)
);

CREATE TABLE member_merge_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    source_member_id uuid NOT NULL REFERENCES member(id),
    target_member_id uuid NOT NULL REFERENCES member(id),
    reason varchar(500),
    operator_id varchar(200),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_merge_source ON member_merge_history(program_id, source_member_id);

-- Canonical mapping for merge (view/query layer).
CREATE TABLE canonical_member_mapping (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    source_member_id uuid NOT NULL,
    target_member_id uuid NOT NULL,
    effective_from timestamptz NOT NULL DEFAULT now(),
    effective_to timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_canonical_mapping UNIQUE (program_id, source_member_id)
);

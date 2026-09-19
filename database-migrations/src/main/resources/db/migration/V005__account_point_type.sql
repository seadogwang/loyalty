-- V005: account + point_type (design 8.4).

CREATE TABLE account (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    member_id uuid NOT NULL REFERENCES member(id),
    account_no varchar(100) NOT NULL,
    account_type varchar(32) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    opened_at timestamptz NOT NULL DEFAULT now(),
    closed_at timestamptz,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uk_account_program_no UNIQUE (program_id, account_no),
    CONSTRAINT uk_account_scope UNIQUE (tenant_id, program_id, id),
    CONSTRAINT uk_account_member_scope UNIQUE (tenant_id, program_id, member_id, id),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))
);
CREATE INDEX idx_account_member_status
ON account(tenant_id, program_id, member_id, status);
-- Scope-aware composite key consumed by point_account_lock.
ALTER TABLE account
    ADD CONSTRAINT uk_account_program_member_id UNIQUE (program_id, id);

CREATE TABLE point_type (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    redeemable boolean NOT NULL DEFAULT false,
    tier_calculable boolean NOT NULL DEFAULT false,
    record_only boolean NOT NULL DEFAULT false,
    consumption_policy varchar(32) NOT NULL DEFAULT 'FEFO',
    validity_type varchar(32) NOT NULL DEFAULT 'NEVER',
    validity_period integer,
    expiration_rule_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_point_type_code UNIQUE (program_id, code),
    CONSTRAINT uk_point_type_scope UNIQUE (tenant_id, program_id, id),
    CONSTRAINT ck_point_type_record_only CHECK (NOT (record_only = true AND redeemable = true)),
    CONSTRAINT ck_point_type_consumption_policy CHECK (consumption_policy IN ('FEFO','FIFO','CUSTOM'))
);

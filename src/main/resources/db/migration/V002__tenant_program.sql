-- V002: tenant + program (design 8.2).
-- Tenant is the platform isolation boundary; Program is the loyalty root business boundary.

CREATE TABLE tenant (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_tenant_code UNIQUE (code)
);

CREATE TABLE program (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    timezone varchar(64) NOT NULL DEFAULT 'UTC',
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    -- Scope-aware composite uniqueness (parent of all scope-aware composite FKs).
    CONSTRAINT uk_program_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_program_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_program_tenant_status ON program(tenant_id, status);

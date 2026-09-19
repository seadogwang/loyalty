-- V007: tier domain (design 8.6).
-- rule_version_id FK resolves because rule_version was created in V003.

CREATE TABLE tier_scheme (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    evaluation_period_type varchar(32) NOT NULL,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_tier_scheme_code UNIQUE (program_id, code),
    CONSTRAINT ck_tier_scheme_period_type
        CHECK (evaluation_period_type IN ('ROLLING','FIXED'))
);

CREATE TABLE tier (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    tier_scheme_id uuid NOT NULL REFERENCES tier_scheme(id),
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    rank_no integer NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uk_tier_code UNIQUE (tier_scheme_id, code),
    CONSTRAINT uk_tier_rank UNIQUE (tier_scheme_id, rank_no)
);

CREATE TABLE tier_rule (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    tier_scheme_id uuid NOT NULL REFERENCES tier_scheme(id),
    rule_type varchar(32) NOT NULL,
    transition_mode varchar(32) NOT NULL DEFAULT 'DIRECT',
    priority integer NOT NULL DEFAULT 100,
    rule_version_id uuid REFERENCES rule_version(id),
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_tier_rule_type
        CHECK (rule_type IN ('UPGRADE','DOWNGRADE','MAINTAIN')),
    CONSTRAINT ck_tier_rule_transition
        CHECK (transition_mode IN ('DIRECT','STEP'))
);
CREATE INDEX idx_tier_rule_scheme_type
ON tier_rule(tier_scheme_id, rule_type, status);

CREATE TABLE member_tier (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    member_id uuid NOT NULL REFERENCES member(id),
    tier_scheme_id uuid NOT NULL REFERENCES tier_scheme(id),
    tier_id uuid NOT NULL REFERENCES tier(id),
    evaluation_period_id varchar(100),
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    reason varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_member_tier_status CHECK (status IN ('ACTIVE','CLOSED'))
);
-- At most one ACTIVE member tier per (member, scheme) (design 2.2 invariant 9).
CREATE UNIQUE INDEX uk_member_tier_active
ON member_tier (member_id, tier_scheme_id)
WHERE status = 'ACTIVE' AND effective_to IS NULL;
CREATE INDEX idx_member_tier_current
ON member_tier(tenant_id, program_id, member_id, tier_scheme_id, status);

CREATE TABLE tier_evaluation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    member_id uuid NOT NULL REFERENCES member(id),
    tier_scheme_id uuid NOT NULL REFERENCES tier_scheme(id),
    evaluation_period_id varchar(100) NOT NULL,
    period_start timestamptz NOT NULL,
    period_end timestamptz NOT NULL,
    current_tier_id uuid REFERENCES tier(id),
    target_tier_id uuid REFERENCES tier(id),
    decision varchar(32) NOT NULL,
    transition_mode varchar(32),
    source_event_id uuid,
    idempotency_key varchar(256) NOT NULL,
    rule_id uuid,
    rule_version varchar(64),
    input_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
    evaluated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_tier_evaluation_decision
        CHECK (decision IN ('UPGRADE','MAINTAIN','DOWNGRADE')),
    CONSTRAINT uk_tier_evaluation_idempotency
        UNIQUE (tenant_id, program_id, member_id, tier_scheme_id, evaluation_period_id, idempotency_key)
);
CREATE INDEX idx_tier_eval_member_scheme
ON tier_evaluation(program_id, member_id, tier_scheme_id, period_start);

-- V008: benefit domain (design 8.7).
-- benefit_rule.rule_version_id FK resolves (rule_version created in V003).

CREATE TABLE benefit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL REFERENCES program(id),
    code varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    benefit_type varchar(64) NOT NULL,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uk_benefit_code UNIQUE (program_id, code)
);

CREATE TABLE benefit_rule (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    benefit_id uuid NOT NULL REFERENCES benefit(id),
    rule_version_id uuid REFERENCES rule_version(id),
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE'
);
CREATE INDEX idx_benefit_rule_benefit ON benefit_rule(benefit_id);

CREATE TABLE tier_benefit_mapping (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    tier_id uuid NOT NULL REFERENCES tier(id),
    benefit_id uuid NOT NULL REFERENCES benefit(id),
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    CONSTRAINT uk_tier_benefit UNIQUE (tier_id, benefit_id, effective_from)
);

CREATE TABLE member_benefit (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    member_id uuid NOT NULL REFERENCES member(id),
    benefit_id uuid NOT NULL REFERENCES benefit(id),
    source_type varchar(32),
    source_id varchar(200),
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_member_benefit
ON member_benefit(tenant_id, program_id, member_id, status);

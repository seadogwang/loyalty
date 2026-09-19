-- V006: point_operation / point_ledger / point_allocation / point_account_lock (design 8.5).
-- The append-only fact core. Scope-aware composite FKs enforce tenant+program+scope
-- invariants at the database layer; triggers reject mutation and validate allocations.

CREATE TABLE point_operation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    member_id uuid NOT NULL,
    account_id uuid NOT NULL,
    point_type_id uuid NOT NULL,
    operation_type varchar(32) NOT NULL,
    idempotency_key varchar(256) NOT NULL,
    request_hash varchar(128) NOT NULL,
    actor_id varchar(200),
    reason_code varchar(100),
    correlation_id varchar(200),
    status varchar(32) NOT NULL DEFAULT 'PROCESSING',
    response_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    -- Global idempotency boundary (design 23.1): tenant_id + idempotency_key.
    CONSTRAINT uk_point_operation UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uk_point_operation_scope UNIQUE (tenant_id, program_id, id),
    CONSTRAINT uk_point_operation_context
        UNIQUE (tenant_id, program_id, member_id, account_id, point_type_id, id),
    CONSTRAINT ck_point_operation_type
        CHECK (operation_type IN ('EARN','REDEEM','EXPIRE','REVERSE','ADJUST','RECALCULATE','RESTORE')),
    CONSTRAINT ck_point_operation_status
        CHECK (status IN ('PROCESSING','COMPLETED','FAILED'))
);

CREATE TABLE point_ledger (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    member_id uuid NOT NULL,
    account_id uuid NOT NULL REFERENCES account(id),
    point_type_id uuid NOT NULL REFERENCES point_type(id),
    transaction_type varchar(32) NOT NULL,
    amount numeric(20,2) NOT NULL,
    effective_at timestamptz NOT NULL,
    expire_at timestamptz,
    source_type varchar(32),
    source_id varchar(200),
    reference_ledger_id uuid,
    operation_id uuid NOT NULL REFERENCES point_operation(id),
    rule_id uuid,
    rule_version_id uuid REFERENCES rule_version(id),
    rule_version varchar(64),
    correlation_id varchar(200),
    calculation_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_point_ledger_scope
        UNIQUE (tenant_id, program_id, member_id, account_id, point_type_id, id),
    CONSTRAINT ck_point_ledger_transaction_type
        CHECK (transaction_type IN ('EARN','REDEEM','EXPIRE','REVERSE','ADJUST','RECALCULATE','RESTORE')),
    CONSTRAINT ck_point_ledger_amount_sign
        CHECK (
            (transaction_type IN ('EARN','RESTORE') AND amount > 0)
            OR (transaction_type IN ('REDEEM','EXPIRE') AND amount < 0)
            OR (transaction_type IN ('REVERSE','ADJUST','RECALCULATE') AND amount <> 0)
        )
);
CREATE INDEX idx_ledger_account_type_effective
ON point_ledger (tenant_id, program_id, account_id, point_type_id, effective_at);
CREATE INDEX idx_ledger_member_type_effective
ON point_ledger (tenant_id, program_id, member_id, point_type_id, effective_at);
CREATE INDEX idx_ledger_source
ON point_ledger (tenant_id, program_id, source_type, source_id);
CREATE INDEX idx_ledger_expire
ON point_ledger (tenant_id, program_id, account_id, point_type_id, expire_at);

CREATE TABLE point_allocation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    member_id uuid NOT NULL,
    account_id uuid NOT NULL REFERENCES account(id),
    point_type_id uuid NOT NULL REFERENCES point_type(id),
    transaction_ledger_id uuid NOT NULL REFERENCES point_ledger(id),
    asset_ledger_id uuid NOT NULL REFERENCES point_ledger(id),
    reference_allocation_id uuid NULL REFERENCES point_allocation(id),
    allocation_type varchar(32) NOT NULL,
    amount numeric(20,2) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_point_allocation_scope
        UNIQUE (tenant_id, program_id, id),
    CONSTRAINT ck_point_allocation_type
        CHECK (allocation_type IN ('CONSUME','EXPIRE','REVERSE','RESTORE','ADJUST')),
    CONSTRAINT ck_point_allocation_amount CHECK (amount > 0)
);

-- Scope-aware composite FKs (design 9.7).
ALTER TABLE point_operation
    ADD CONSTRAINT fk_operation_program_scope
    FOREIGN KEY (tenant_id, program_id) REFERENCES program(tenant_id, id),
    ADD CONSTRAINT fk_operation_member_scope
    FOREIGN KEY (tenant_id, program_id, member_id) REFERENCES member(tenant_id, program_id, id),
    ADD CONSTRAINT fk_operation_account_member_scope
    FOREIGN KEY (tenant_id, program_id, member_id, account_id)
        REFERENCES account(tenant_id, program_id, member_id, id),
    ADD CONSTRAINT fk_operation_point_type_scope
    FOREIGN KEY (tenant_id, program_id, point_type_id) REFERENCES point_type(tenant_id, program_id, id);

ALTER TABLE point_ledger
    ADD CONSTRAINT fk_ledger_member_scope_full
    FOREIGN KEY (tenant_id, program_id, member_id) REFERENCES member(tenant_id, program_id, id),
    ADD CONSTRAINT fk_ledger_account_member_scope
    FOREIGN KEY (tenant_id, program_id, member_id, account_id)
        REFERENCES account(tenant_id, program_id, member_id, id),
    ADD CONSTRAINT fk_ledger_point_type_scope
    FOREIGN KEY (tenant_id, program_id, point_type_id) REFERENCES point_type(tenant_id, program_id, id),
    ADD CONSTRAINT fk_ledger_operation_scope
    FOREIGN KEY (tenant_id, program_id, member_id, account_id, point_type_id, operation_id)
        REFERENCES point_operation(tenant_id, program_id, member_id, account_id, point_type_id, id);

ALTER TABLE point_allocation
    ADD CONSTRAINT fk_allocation_member_scope
    FOREIGN KEY (tenant_id, program_id, member_id) REFERENCES member(tenant_id, program_id, id),
    ADD CONSTRAINT fk_allocation_account_member_scope
    FOREIGN KEY (tenant_id, program_id, member_id, account_id)
        REFERENCES account(tenant_id, program_id, member_id, id),
    ADD CONSTRAINT fk_allocation_point_type_scope
    FOREIGN KEY (tenant_id, program_id, point_type_id) REFERENCES point_type(tenant_id, program_id, id),
    ADD CONSTRAINT fk_allocation_transaction_scope
    FOREIGN KEY (tenant_id, program_id, member_id, account_id, point_type_id, transaction_ledger_id)
        REFERENCES point_ledger(tenant_id, program_id, member_id, account_id, point_type_id, id),
    ADD CONSTRAINT fk_allocation_asset_scope
    FOREIGN KEY (tenant_id, program_id, member_id, account_id, point_type_id, asset_ledger_id)
        REFERENCES point_ledger(tenant_id, program_id, member_id, account_id, point_type_id, id),
    ADD CONSTRAINT fk_allocation_reference_scope
    FOREIGN KEY (tenant_id, program_id, reference_allocation_id)
        REFERENCES point_allocation(tenant_id, program_id, id);

-- Validate allocation invariants on INSERT (design 8.5 validate_point_allocation).
CREATE OR REPLACE FUNCTION loyalty.validate_point_allocation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    transaction_kind varchar(32);
    transaction_amount numeric(20,2);
    asset_kind varchar(32);
    asset_amount numeric(20,2);
    reference_kind varchar(32);
BEGIN
    SELECT transaction_type, amount
      INTO transaction_kind, transaction_amount
      FROM point_ledger
     WHERE id = NEW.transaction_ledger_id;
    SELECT transaction_type, amount
      INTO asset_kind, asset_amount
      FROM point_ledger
     WHERE id = NEW.asset_ledger_id;
    IF transaction_kind IS NULL OR asset_kind IS NULL THEN
        RAISE EXCEPTION 'allocation ledger references must exist';
    END IF;
    IF asset_kind NOT IN ('EARN','ADJUST','RECALCULATE') OR asset_amount <= 0 THEN
        RAISE EXCEPTION 'allocation asset must be a positive redeemable asset';
    END IF;
    IF (NEW.allocation_type = 'CONSUME' AND transaction_kind <> 'REDEEM')
       OR (NEW.allocation_type = 'EXPIRE' AND transaction_kind <> 'EXPIRE')
       OR (NEW.allocation_type = 'REVERSE' AND transaction_kind <> 'REVERSE')
       OR (NEW.allocation_type = 'RESTORE' AND transaction_kind <> 'RESTORE')
       OR (NEW.allocation_type = 'ADJUST'
           AND transaction_kind NOT IN ('ADJUST','RECALCULATE')) THEN
        RAISE EXCEPTION 'allocation type does not match transaction type';
    END IF;
    IF NEW.allocation_type = 'ADJUST' AND transaction_amount >= 0 THEN
        RAISE EXCEPTION 'ADJUST allocation requires a negative adjustment ledger';
    END IF;
    IF NEW.allocation_type = 'RESTORE' THEN
        SELECT allocation_type INTO reference_kind
          FROM point_allocation
         WHERE id = NEW.reference_allocation_id;
        IF reference_kind <> 'CONSUME' THEN
            RAISE EXCEPTION 'RESTORE must reference a CONSUME allocation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_point_allocation_validate
    BEFORE INSERT ON point_allocation
    FOR EACH ROW EXECUTE FUNCTION loyalty.validate_point_allocation();

CREATE TRIGGER trg_point_ledger_append_only
    BEFORE UPDATE OR DELETE ON point_ledger
    FOR EACH ROW EXECUTE FUNCTION loyalty.reject_append_only_mutation();

CREATE TRIGGER trg_point_allocation_append_only
    BEFORE UPDATE OR DELETE ON point_allocation
    FOR EACH ROW EXECUTE FUNCTION loyalty.reject_append_only_mutation();

CREATE INDEX idx_allocation_asset
ON point_allocation (tenant_id, program_id, account_id, point_type_id, asset_ledger_id);
CREATE INDEX idx_allocation_transaction
ON point_allocation (tenant_id, program_id, account_id, point_type_id, transaction_ledger_id);
CREATE INDEX idx_allocation_reference
ON point_allocation (reference_allocation_id);

CREATE TABLE point_account_lock (
    tenant_id uuid NOT NULL,
    program_id uuid NOT NULL,
    account_id uuid NOT NULL,
    point_type_id uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, program_id, account_id, point_type_id),
    CONSTRAINT fk_lock_account_scope
        FOREIGN KEY (tenant_id, program_id, account_id) REFERENCES account(tenant_id, program_id, id),
    CONSTRAINT fk_lock_point_type_scope
        FOREIGN KEY (tenant_id, program_id, point_type_id) REFERENCES point_type(tenant_id, program_id, id)
);

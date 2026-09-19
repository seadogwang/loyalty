-- V015: harden validate_point_allocation to schema-qualified references.
-- The original (V006) used unqualified point_ledger/point_allocation, which fail when the
-- application connection's search_path does not include the loyalty schema. Functions run
-- in the caller's search_path, so all table references inside must be schema-qualified.

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
      FROM loyalty.point_ledger
     WHERE id = NEW.transaction_ledger_id;
    SELECT transaction_type, amount
      INTO asset_kind, asset_amount
      FROM loyalty.point_ledger
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
          FROM loyalty.point_allocation
         WHERE id = NEW.reference_allocation_id;
        IF reference_kind <> 'CONSUME' THEN
            RAISE EXCEPTION 'RESTORE must reference a CONSUME allocation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

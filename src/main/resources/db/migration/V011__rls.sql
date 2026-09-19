-- V011: Row Level Security (design 18 / 27.5).
-- Defense-in-depth on top of application-layer scope checks. A trusted component sets
-- app.tenant_id / app.program_id / app.principal_id per transaction; RLS then guarantees
-- that a query which forgets a tenant filter cannot leak cross-tenant rows.
--
-- Policy stance (deny by default, design 27):
--   * strict tenant tables:   a row is visible/writable iff tenant_id == app.tenant_id;
--   * nullable tenant tables: system-scoped rows (tenant_id IS NULL) are visible to all,
--     tenant-scoped rows only to their tenant.
-- FORCE ROW LEVEL SECURITY so the table owner (app runtime role) is also subject; only
-- the migration / admin superuser role (BYPASSRLS) is exempt.

CREATE OR REPLACE FUNCTION loyalty.current_tenant_id()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(btrim(current_setting('app.tenant_id', true)), NULL)::uuid
$$;

CREATE OR REPLACE FUNCTION loyalty.current_program_id()
RETURNS uuid
LANGUAGE sql
STABLE
AS $$
    SELECT NULLIF(btrim(current_setting('app.program_id', true)), NULL)::uuid
$$;

-- Strict tenant-scoped tables.
CREATE OR REPLACE FUNCTION loyalty.apply_tenant_rls(tbl regclass)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', tbl);
    EXECUTE format($f$
        DROP POLICY IF EXISTS tenant_isolation ON %s;
        CREATE POLICY tenant_isolation ON %s
        FOR ALL
        USING (tenant_id = loyalty.current_tenant_id())
        WITH CHECK (tenant_id = loyalty.current_tenant_id())
    $f$, tbl, tbl);
END;
$$;

-- Nullable-tenant tables (auth_role / audit / approval): system rows visible to all,
-- tenant rows only to their tenant.
CREATE OR REPLACE FUNCTION loyalty.apply_nullable_tenant_rls(tbl regclass)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', tbl);
    EXECUTE format($f$
        DROP POLICY IF EXISTS tenant_isolation ON %s;
        CREATE POLICY tenant_isolation ON %s
        FOR ALL
        USING (tenant_id IS NULL OR tenant_id = loyalty.current_tenant_id())
        WITH CHECK (tenant_id IS NULL OR tenant_id = loyalty.current_tenant_id())
    $f$, tbl, tbl);
END;
$$;

-- Strict tenant-scoped business tables.
SELECT loyalty.apply_tenant_rls('loyalty.program');
SELECT loyalty.apply_tenant_rls('loyalty.member');
SELECT loyalty.apply_tenant_rls('loyalty.member_identity');
SELECT loyalty.apply_tenant_rls('loyalty.member_attribute_definition');
SELECT loyalty.apply_tenant_rls('loyalty.member_attribute_value');
SELECT loyalty.apply_tenant_rls('loyalty.member_merge_history');
SELECT loyalty.apply_tenant_rls('loyalty.canonical_member_mapping');
SELECT loyalty.apply_tenant_rls('loyalty.account');
SELECT loyalty.apply_tenant_rls('loyalty.point_type');
SELECT loyalty.apply_tenant_rls('loyalty.point_operation');
SELECT loyalty.apply_tenant_rls('loyalty.point_ledger');
SELECT loyalty.apply_tenant_rls('loyalty.point_allocation');
SELECT loyalty.apply_tenant_rls('loyalty.point_account_lock');
SELECT loyalty.apply_tenant_rls('loyalty.tier_scheme');
SELECT loyalty.apply_tenant_rls('loyalty.tier');
SELECT loyalty.apply_tenant_rls('loyalty.tier_rule');
SELECT loyalty.apply_tenant_rls('loyalty.member_tier');
SELECT loyalty.apply_tenant_rls('loyalty.tier_evaluation');
SELECT loyalty.apply_tenant_rls('loyalty.benefit');
SELECT loyalty.apply_tenant_rls('loyalty.benefit_rule');
SELECT loyalty.apply_tenant_rls('loyalty.tier_benefit_mapping');
SELECT loyalty.apply_tenant_rls('loyalty.member_benefit');
SELECT loyalty.apply_tenant_rls('loyalty.rule_definition');
SELECT loyalty.apply_tenant_rls('loyalty.rule_version');
SELECT loyalty.apply_tenant_rls('loyalty.rule_execution_audit');
SELECT loyalty.apply_tenant_rls('loyalty.outbox_event');
SELECT loyalty.apply_tenant_rls('loyalty.inbox_event');

-- Nullable-tenant authorization tables.
SELECT loyalty.apply_nullable_tenant_rls('loyalty.auth_role');
SELECT loyalty.apply_nullable_tenant_rls('loyalty.auth_principal_role');
SELECT loyalty.apply_nullable_tenant_rls('loyalty.authorization_audit');
SELECT loyalty.apply_nullable_tenant_rls('loyalty.authorization_approval');

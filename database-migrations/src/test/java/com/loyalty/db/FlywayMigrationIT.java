package com.loyalty.db;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the Flyway migration suite on a fresh embedded PostgreSQL, and asserts the
 * core data-integrity invariants: append-only guards, allocation trigger validation,
 * scope-aware composite FK rejection, and RLS tenant isolation (deny-by-default).
 */
class FlywayMigrationIT {

    private static EmbeddedPostgres pg;
    private static DataSource ds;

    @BeforeAll
    static void setup() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        ds = pg.getPostgresDatabase();
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .schemas("loyalty")
                .defaultSchema("loyalty")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        // Non-superuser, non-owner role for RLS tests.
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE ROLE loyalty_app LOGIN PASSWORD 'app'");
            s.execute("GRANT USAGE ON SCHEMA loyalty TO loyalty_app");
            s.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA loyalty TO loyalty_app");
        }
    }

    @AfterAll
    static void teardown() throws Exception {
        if (pg != null) {
            pg.close();
        }
    }

    private Connection superConn() throws SQLException {
        return ds.getConnection();
    }

    private Connection appConn() throws SQLException {
        // Superuser connection that immediately drops to the non-superuser app role via
        // SET ROLE. This suspends superuser privileges so RLS applies, while avoiding the
        // ambiguous getDatabase(user, password) DataSource API.
        Connection c = ds.getConnection();
        try (Statement s = c.createStatement()) {
            s.execute("SET ROLE loyalty_app");
        }
        return c;
    }

    @Test
    void migrationsAppliedAndSeedPresent() throws Exception {
        try (Connection c = superConn(); Statement s = c.createStatement()) {
            // Core tables exist.
            s.execute("SELECT 1 FROM loyalty.point_ledger LIMIT 0");
            s.execute("SELECT 1 FROM loyalty.point_allocation LIMIT 0");
            s.execute("SELECT 1 FROM loyalty.auth_role LIMIT 0");

            try (ResultSet r = s.executeQuery("SELECT count(*) FROM loyalty.auth_permission")) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(22);
            }
            try (ResultSet r = s.executeQuery("SELECT count(*) FROM loyalty.auth_role WHERE managed = true")) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(11);
            }
            try (ResultSet r = s.executeQuery(
                    "SELECT count(*) FROM loyalty.auth_api_permission WHERE http_method='POST' AND path_template LIKE '%/point-operations/earn'")) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void appendOnlyLedgerRejectsUpdateAndDelete() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID program = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID pointType = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        seedPointContext(tenant, program, member, account, pointType, "BASIC", true, false);

        try (Connection c = superConn(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            insertOperation(s, tenant, program, member, account, pointType, operation, "EARN");
            UUID ledger = insertLedger(s, tenant, program, member, account, pointType, operation,
                    "EARN", "100.00", program); // effective_at uses now()
            c.commit();

            // UPDATE must be rejected by the append-only trigger.
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE loyalty.point_ledger SET amount = amount WHERE id = ?")) {
                    ps.setObject(1, ledger);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class);

            // DELETE must also be rejected.
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM loyalty.point_ledger WHERE id = ?")) {
                    ps.setObject(1, ledger);
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class);
            c.rollback();
        }
    }

    @Test
    void allocationTriggerRejectsTypeMismatch() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID program = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID pointType = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        seedPointContext(tenant, program, member, account, pointType, "RWD", true, false);

        try (Connection c = superConn(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            insertOperation(s, tenant, program, member, account, pointType, operation, "EARN");
            UUID earnLedger = insertLedger(s, tenant, program, member, account, pointType, operation,
                    "EARN", "100.00", null);

            // A REDEEM transaction ledger consuming the asset.
            UUID redeemOp = UUID.randomUUID();
            insertOperation(s, tenant, program, member, account, pointType, redeemOp, "REDEEM");
            UUID redeemLedger = insertLedger(s, tenant, program, member, account, pointType, redeemOp,
                    "REDEEM", "-40.00", null);

            // allocation_type=EXPIRE against a REDEEM transaction ledger -> trigger rejects.
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO loyalty.point_allocation " +
                        "(id, tenant_id, program_id, member_id, account_id, point_type_id, " +
                        " transaction_ledger_id, asset_ledger_id, allocation_type, amount) " +
                        "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, 'EXPIRE', 40.00)")) {
                    int i = 0;
                    ps.setObject(++i, tenant);
                    ps.setObject(++i, program);
                    ps.setObject(++i, member);
                    ps.setObject(++i, account);
                    ps.setObject(++i, pointType);
                    ps.setObject(++i, redeemLedger); // transaction
                    ps.setObject(++i, earnLedger);   // asset
                    ps.executeUpdate();
                }
            }).isInstanceOf(SQLException.class);
            c.rollback();
        }
    }

    @Test
    void scopeAwareCompositeFkRejectsCrossScopeOperation() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID program = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID bogusAccount = UUID.randomUUID(); // not seeded in this scope
        UUID pointType = UUID.randomUUID();
        seedPointContext(tenant, program, member, account, pointType, "SCOPE", true, false);

        try (Connection c = superConn(); Statement s = c.createStatement()) {
            // point_operation.account_id has no plain FK; the scope-aware composite FK
            // fk_operation_account_member_scope enforces (tenant,program,member,account)
            // must exist in account. A bogus account in this scope is rejected.
            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO loyalty.point_operation " +
                    "(tenant_id, program_id, member_id, account_id, point_type_id, operation_type, idempotency_key, request_hash, status) " +
                    "VALUES ('" + tenant + "','" + program + "','" + member + "','" + bogusAccount + "','" + pointType + "','EARN','k1','h','PROCESSING')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void rlsDeniesByDefaultAndScopesToTenant() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID programA = UUID.randomUUID();
        UUID programB = UUID.randomUUID();
        seedTenantProgram(tenantA, programA, "TA", "PA");
        seedTenantProgram(tenantB, programB, "TB", "PB");

        // Seed one member per tenant (as superuser, bypasses RLS).
        try (Connection c = superConn(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) " +
                    "VALUES (gen_random_uuid(),'" + tenantA + "','" + programA + "','A-001')");
            s.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) " +
                    "VALUES (gen_random_uuid(),'" + tenantB + "','" + programB + "','B-001')");
        }

        // loyalty_app with NO tenant context sees nothing (deny by default).
        try (Connection c = appConn(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT count(*) FROM loyalty.member")) {
                r.next();
                assertThat(r.getInt(1)).isZero();
            }
        }

        // With tenant A context, only A's member is visible.
        try (Connection c = appConn(); Statement s = c.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantA + "'");
            try (ResultSet r = s.executeQuery("SELECT member_no FROM loyalty.member")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("A-001");
                assertThat(r.next()).isFalse();
            }
            // Inserting a member for tenant B under tenant A context is rejected by WITH CHECK.
            assertThatThrownBy(() -> s.execute(
                    "INSERT INTO loyalty.member (tenant_id, program_id, member_no) " +
                    "VALUES ('" + tenantB + "','" + programB + "','B-LEAK')"))
                    .isInstanceOf(SQLException.class);
        }

        // Switching the same connection to tenant B must not leak tenant A rows.
        try (Connection c = appConn(); Statement s = c.createStatement()) {
            s.execute("SET app.tenant_id = '" + tenantA + "'");
            s.execute("RESET app.tenant_id");
            s.execute("SET app.tenant_id = '" + tenantB + "'");
            try (ResultSet r = s.executeQuery("SELECT member_no FROM loyalty.member")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(1)).isEqualTo("B-001");
                assertThat(r.next()).isFalse();
            }
        }
    }

    // ---- helpers -----------------------------------------------------------

    private void seedTenantProgram(UUID tenant, UUID program, String tcode, String pcode) throws SQLException {
        // Use the UUID as the code to keep every test method unique (uk_tenant_code / uk_program_tenant_code).
        String code = tenant.toString();
        try (Connection c = superConn(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenant + "','" + code + "','" + tcode + "')");
            s.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + program + "','" + tenant + "','" + program + "','" + pcode + "')");
        }
    }

    private void seedPointContext(UUID tenant, UUID program, UUID member, UUID account, UUID pointType,
                                  String ptCode, boolean redeemable, boolean tierCalculable) throws SQLException {
        try (Connection c = superConn(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenant + "','" + tenant + "','T')");
            s.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + program + "','" + tenant + "','" + program + "','P')");
            s.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) " +
                    "VALUES ('" + member + "','" + tenant + "','" + program + "','" + member + "')");
            s.execute("INSERT INTO loyalty.account (id, tenant_id, program_id, member_id, account_no, account_type) " +
                    "VALUES ('" + account + "','" + tenant + "','" + program + "','" + member + "','" + account + "','LOYALTY')");
            s.execute("INSERT INTO loyalty.point_type (id, tenant_id, program_id, code, name, redeemable, tier_calculable) " +
                    "VALUES ('" + pointType + "','" + tenant + "','" + program + "','" + ptCode + "','" + ptCode + "'," +
                    redeemable + "," + tierCalculable + ")");
        }
    }

    private void insertOperation(Statement s, UUID tenant, UUID program, UUID member, UUID account,
                                 UUID pointType, UUID operation, String type) throws SQLException {
        s.execute("INSERT INTO loyalty.point_operation " +
                "(id, tenant_id, program_id, member_id, account_id, point_type_id, operation_type, idempotency_key, request_hash, status) " +
                "VALUES ('" + operation + "','" + tenant + "','" + program + "','" + member + "','" + account + "','" + pointType + "','" + type + "','" + operation + ":k','hash','COMPLETED')");
    }

    /** @return the inserted ledger id. */
    private UUID insertLedger(Statement s, UUID tenant, UUID program, UUID member, UUID account,
                              UUID pointType, UUID operation, String type, String amount, Object ref) throws SQLException {
        UUID ledger = UUID.randomUUID();
        StringBuilder sql = new StringBuilder("INSERT INTO loyalty.point_ledger " +
                "(id, tenant_id, program_id, member_id, account_id, point_type_id, transaction_type, amount, effective_at, operation_id");
        if (ref != null) sql.append(", source_type, source_id");
        sql.append(") VALUES ('").append(ledger).append("','").append(tenant).append("','").append(program)
                .append("','").append(member).append("','").append(account).append("','").append(pointType)
                .append("','").append(type).append("',").append(amount).append(",now(),'").append(operation).append("'");
        if (ref != null) sql.append(",'ORDER','").append(ref).append("'");
        sql.append(")");
        s.execute(sql.toString());
        return ledger;
    }
}

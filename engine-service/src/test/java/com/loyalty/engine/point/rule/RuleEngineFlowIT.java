package com.loyalty.engine.point.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.engine.EngineServiceApplication;
import com.loyalty.engine.point.rule.fact.OrderFact;
import com.loyalty.engine.point.rule.fact.PointTypeFact;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M6-T02 Drools runtime (engine): golden case — a DRL rule computes points from facts. */
@SpringBootTest(classes = EngineServiceApplication.class)
@Import(RuleEngineFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuleEngineFlowIT {

    private static UUID tenantId, programId, ruleDefId, ruleVersionId;
    private static UUID pointTypeId;
    private static EmbeddedPostgres pg;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("loyalty.security.jwt.symmetric-key", () -> "test-secret-key-at-least-32-bytes-long-xx");
        r.add("loyalty.security.jwt.issuer-uri", () -> "https://idp.test/auth");
        r.add("loyalty.security.jwt.audience", () -> "loyalty-platform");
        r.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
        r.add("eureka.client.enabled", () -> "false");
        r.add("spring.cloud.discovery.enabled", () -> "false");
        r.add("spring.cache.type", () -> "simple");
        r.add("loyalty.outbox.enabled", () -> "false");
    }

    @TestConfiguration
    static class EmbeddedPgConfig {
        @Bean(destroyMethod = "close")
        EmbeddedPostgres embeddedPostgres() throws Exception { pg = EmbeddedPostgres.builder().start(); return pg; }
        @Bean @Primary DataSource dataSource(EmbeddedPostgres pg) { return pg.getPostgresDatabase(); }
    }

    private static final String DRL = """
            package com.loyalty.engine.point.rule;
            import com.loyalty.engine.point.rule.fact.OrderFact;
            import com.loyalty.engine.point.rule.fact.PointTypeFact;
            import com.loyalty.engine.point.rule.RuleEvaluationResult;
            rule "ORDER_EARN_BASIC"
            when
              $order : OrderFact(totalNetAmount > 0)
              $pt : PointTypeFact(code == "BASIC", redeemable == true)
              $r : RuleEvaluationResult()
            then
              $r.addPoint($pt.pointTypeId(), $order.totalNetAmount(), "ORDER_EARN");
            end
            """;

    @BeforeAll
    void seed(@Autowired DataSource ds, @Autowired ObjectMapper json) throws Exception {
        tenantId = UUID.randomUUID(); programId = UUID.randomUUID();
        ruleDefId = UUID.randomUUID(); ruleVersionId = UUID.randomUUID();
        pointTypeId = UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.rule_definition (id, tenant_id, program_id, code, name, domain) " +
                "VALUES ('" + ruleDefId + "','" + tenantId + "','" + programId + "','ORDER_EARN','Order Earn','point')");
        String metadata = json.writeValueAsString(java.util.Map.of("drl", DRL));
        jdbc.update("INSERT INTO loyalty.rule_version (id, tenant_id, program_id, rule_definition_id, version_no, status, metadata, created_by) " +
                "VALUES (?,?,?,?,'1','PUBLISHED',?::jsonb,'author')", ruleVersionId, tenantId, programId, ruleDefId, metadata);
    }

    @AfterAll
    static void teardown() { /* PG closed by context. */ }

    @Test
    void executesGoldenCaseAndAudits(@Autowired RuleEngineService engine, @Autowired DataSource ds) {
        OrderFact order = new OrderFact("O1", "WEB", Instant.now(), new BigDecimal("120.00"), "SGD");
        PointTypeFact pt = new PointTypeFact(pointTypeId, "BASIC", true, true, false);
        RuleEvaluationResult result = engine.execute(tenantId, programId, ruleDefId, "corr-1",
                List.of(order, pt));

        assertThat(result.getPointResults()).hasSize(1);
        assertThat(result.getPointResults().get(0).pointTypeId()).isEqualTo(pointTypeId);
        assertThat(result.getPointResults().get(0).amount()).isEqualByComparingTo("120.00");
        assertThat(result.getPointResults().get(0).reason()).isEqualTo("ORDER_EARN");

        Integer audits = new JdbcTemplate(ds).queryForObject(
                "SELECT count(*) FROM loyalty.rule_execution_audit WHERE rule_version_id=? AND status='SUCCESS'",
                Integer.class, ruleVersionId);
        assertThat(audits).isGreaterThanOrEqualTo(1);
    }
}

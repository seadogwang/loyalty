package com.loyalty.engine.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.engine.EngineServiceApplication;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** M8 order/return/recalculate orchestration (engine). */
@SpringBootTest(classes = EngineServiceApplication.class)
@AutoConfigureMockMvc
@Import(OrderFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFlowIT {

    private static UUID tenantId, programId, memberId, pointTypeId, accountId, ruleDefId;
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
            package com.loyalty.engine.order;
            import com.loyalty.engine.point.rule.fact.OrderFact;
            import com.loyalty.engine.point.rule.fact.PointTypeFact;
            import com.loyalty.engine.point.rule.RuleEvaluationResult;
            rule "ORDER_EARN"
            when $o: OrderFact(totalNetAmount > 0) $pt: PointTypeFact() $r: RuleEvaluationResult()
            then $r.addPoint($pt.pointTypeId(), $o.totalNetAmount(), "ORDER_EARN"); end
            """;

    @BeforeAll
    void seed(@Autowired DataSource ds, @Autowired ObjectMapper json) throws Exception {
        tenantId = UUID.randomUUID(); programId = UUID.randomUUID();
        memberId = UUID.randomUUID(); pointTypeId = UUID.randomUUID();
        accountId = UUID.randomUUID(); ruleDefId = UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.point_type (id, tenant_id, program_id, code, name, redeemable, tier_calculable) " +
                "VALUES ('" + pointTypeId + "','" + tenantId + "','" + programId + "','BASIC','Basic',true,true)");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberId + "','" + tenantId + "','" + programId + "','M1')");
        jdbc.execute("INSERT INTO loyalty.account (id, tenant_id, program_id, member_id, account_no, account_type) " +
                "VALUES ('" + accountId + "','" + tenantId + "','" + programId + "','" + memberId + "','ACC','LOYALTY')");
        jdbc.execute("INSERT INTO loyalty.rule_definition (id, tenant_id, program_id, code, name, domain) " +
                "VALUES ('" + ruleDefId + "','" + tenantId + "','" + programId + "','ORDER_EARN','Order Earn','point')");
        String metadata = json.writeValueAsString(Map.of("drl", DRL));
        jdbc.update("INSERT INTO loyalty.rule_version (id, tenant_id, program_id, rule_definition_id, version_no, status, metadata, created_by) " +
                "VALUES (?,?,?,?,'1','PUBLISHED',?::jsonb,'author')", UUID.randomUUID(), tenantId, programId, ruleDefId, metadata);
        jdbc.execute("INSERT INTO loyalty.auth_principal (principal_type, subject, display_name) VALUES ('USER','operator','Operator') ON CONFLICT DO NOTHING");
        jdbc.execute("INSERT INTO loyalty.auth_principal_role (principal_id, role_id, scope_type, tenant_id, program_id, status) " +
                "SELECT p.id, r.id, 'PROGRAM', '" + tenantId + "', '" + programId + "', 'ACTIVE' " +
                "FROM loyalty.auth_principal p, loyalty.auth_role r WHERE p.subject='operator' AND r.code='PLATFORM_ADMIN' ON CONFLICT DO NOTHING");
    }

    @AfterAll
    static void teardown() { /* PG closed by context. */ }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor operator() {
        return jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()));
    }

    private String orderBody(ObjectMapper json, String orderId, String amount) throws Exception {
        return json.writeValueAsString(Map.of(
                "orderId", orderId, "channel", "WEB", "totalNetAmount", amount, "currency", "SGD",
                "memberId", memberId.toString(), "accountId", accountId.toString(),
                "pointTypeId", pointTypeId.toString(), "ruleDefinitionId", ruleDefId.toString()));
    }

    private String balance(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/programs/" + programId + "/members/" + memberId
                        + "/accounts/" + accountId + "/balances?pointTypeId=" + pointTypeId).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("balances").get(0).get("available").asText();
    }

    @Test
    void orderEarnReturnRecalculate(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        // Order complete -> earn 120 via rule.
        mvc.perform(post("/api/v1/programs/" + programId + "/orders/complete")
                .contentType(MediaType.APPLICATION_JSON).content(orderBody(json, "O1", "120.00")).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("120.00"));
        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(balance(mvc, json)))
                .isEqualByComparingTo("120.00");

        // Return complete -> reverse 120.
        mvc.perform(post("/api/v1/programs/" + programId + "/returns/complete")
                .contentType(MediaType.APPLICATION_JSON).content(orderBody(json, "O1", "120.00")).with(operator()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(new java.math.BigDecimal(balance(mvc, json)))
                .isEqualByComparingTo("0.00");

        // Recalculate -> expected=120 (rule), actual EARN=120 -> delta 0.
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/point-operations/recalculate?mode=CURRENT_RULE")
                .header("Idempotency-Key", "recalc:O1")
                .contentType(MediaType.APPLICATION_JSON).content(orderBody(json, "O1", "120.00")).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("0.00"));
    }
}

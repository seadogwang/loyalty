package com.loyalty.engine.tier;

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

/** M7 tier evaluation: UPGRADE/MAINTAIN, idempotent re-eval, recalculate. */
@SpringBootTest(classes = EngineServiceApplication.class)
@AutoConfigureMockMvc
@Import(TierFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TierFlowIT {

    private static UUID tenantId, programId, memberId, pointTypeId, accountId, schemeId, silverId, goldId, platinumId;
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

    @BeforeAll
    void seed(@Autowired DataSource ds) {
        tenantId = UUID.randomUUID(); programId = UUID.randomUUID();
        memberId = UUID.randomUUID(); pointTypeId = UUID.randomUUID();
        accountId = UUID.randomUUID(); schemeId = UUID.randomUUID();
        silverId = UUID.randomUUID(); goldId = UUID.randomUUID(); platinumId = UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.point_type (id, tenant_id, program_id, code, name, redeemable, tier_calculable) " +
                "VALUES ('" + pointTypeId + "','" + tenantId + "','" + programId + "','BASIC','Basic',true,true)");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberId + "','" + tenantId + "','" + programId + "','M1')");
        jdbc.execute("INSERT INTO loyalty.account (id, tenant_id, program_id, member_id, account_no, account_type) " +
                "VALUES ('" + accountId + "','" + tenantId + "','" + programId + "','" + memberId + "','ACC','LOYALTY')");
        jdbc.execute("INSERT INTO loyalty.tier_scheme (id, tenant_id, program_id, code, name, evaluation_period_type) " +
                "VALUES ('" + schemeId + "','" + tenantId + "','" + programId + "','MS','Membership','ROLLING')");
        jdbc.execute("INSERT INTO loyalty.tier (id, tenant_id, program_id, tier_scheme_id, code, name, rank_no, config_json) " +
                "VALUES ('" + silverId + "','" + tenantId + "','" + programId + "','" + schemeId + "','SILVER','Silver',1,'{\"threshold\":0}')");
        jdbc.execute("INSERT INTO loyalty.tier (id, tenant_id, program_id, tier_scheme_id, code, name, rank_no, config_json) " +
                "VALUES ('" + goldId + "','" + tenantId + "','" + programId + "','" + schemeId + "','GOLD','Gold',2,'{\"threshold\":1000}')");
        jdbc.execute("INSERT INTO loyalty.tier (id, tenant_id, program_id, tier_scheme_id, code, name, rank_no, config_json) " +
                "VALUES ('" + platinumId + "','" + tenantId + "','" + programId + "','" + schemeId + "','PLATINUM','Platinum',3,'{\"threshold\":2000}')");
        jdbc.execute("INSERT INTO loyalty.auth_principal (principal_type, subject, display_name) VALUES ('USER','operator','Operator') ON CONFLICT DO NOTHING");
        jdbc.execute("INSERT INTO loyalty.auth_principal_role (principal_id, role_id, scope_type, tenant_id, program_id, status) " +
                "SELECT p.id, r.id, 'PROGRAM', '" + tenantId + "', '" + programId + "', 'ACTIVE' " +
                "FROM loyalty.auth_principal p, loyalty.auth_role r WHERE p.subject='operator' AND r.code='PROGRAM_ADMIN' ON CONFLICT DO NOTHING");
    }

    @AfterAll
    static void teardown() { /* PG closed by context. */ }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor operator() {
        return jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()));
    }

    private void earn(@Autowired MockMvc mvc, @Autowired ObjectMapper json, String key, String amount) throws Exception {
        String body = json.writeValueAsString(Map.of("pointTypeId", pointTypeId.toString(), "amount", amount,
                "source", Map.of("type", "ORDER", "id", "O" + key)));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + accountId
                        + "/point-operations/earn").header("Idempotency-Key", "tier:" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk());
    }

    private void evaluate(@Autowired MockMvc mvc, @Autowired ObjectMapper json, String expectDecision, UUID expectTier) throws Exception {
        String body = json.writeValueAsString(Map.of("schemeId", schemeId.toString()));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/tier-evaluations")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value(expectDecision))
                .andExpect(jsonPath("$.targetTierId").value(expectTier.toString()));
    }

    @Test
    void upgradeMaintainAndRecalculate(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        earn(mvc, json, "1", "1500.00"); // ranking 1500 -> Gold (1000<=1500<2000)
        evaluate(mvc, json, "UPGRADE", goldId);

        // Current tier = Gold.
        mvc.perform(get("/api/v1/programs/" + programId + "/members/" + memberId + "/tiers").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tierId").value(goldId.toString()));

        // Re-evaluate same day (manual) -> idempotent: returns the stored UPGRADE, no new transition.
        evaluate(mvc, json, "UPGRADE", goldId);
        mvc.perform(get("/api/v1/programs/" + programId + "/members/" + memberId + "/tiers").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tierId").value(goldId.toString()));

        // Earn to 2500, recalculate -> UPGRADE to Platinum.
        earn(mvc, json, "2", "1000.00");
        String body = json.writeValueAsString(Map.of("schemeId", schemeId.toString()));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/tier-evaluations/recalculate")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("UPGRADE"))
                .andExpect(jsonPath("$.targetTierId").value(platinumId.toString()));

        // History: 2 member_tier rows (Gold CLOSED, Platinum ACTIVE).
        mvc.perform(get("/api/v1/programs/" + programId + "/members/" + memberId + "/tiers/" + schemeId + "/history").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}

package com.loyalty.engine.point;

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
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** M4 Reverse / Restore / Adjust semantics. Each test uses its own account. */
@SpringBootTest(classes = EngineServiceApplication.class)
@AutoConfigureMockMvc
@Import(OperationFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationFlowIT {

    private static UUID tenantId, programId, memberId, pointTypeId;

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
    }

    @TestConfiguration
    static class EmbeddedPgConfig {
        static EmbeddedPostgres pg;
        @Bean(destroyMethod = "close")
        EmbeddedPostgres embeddedPostgres() throws Exception { pg = EmbeddedPostgres.builder().start(); return pg; }
        @Bean @Primary DataSource dataSource(EmbeddedPostgres pg) { return pg.getPostgresDatabase(); }
    }

    @BeforeAll
    void seed(@Autowired DataSource ds) {
        tenantId = UUID.randomUUID(); programId = UUID.randomUUID();
        memberId = UUID.randomUUID(); pointTypeId = UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.point_type (id, tenant_id, program_id, code, name, redeemable, tier_calculable) " +
                "VALUES ('" + pointTypeId + "','" + tenantId + "','" + programId + "','BASIC','Basic',true,true)");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberId + "','" + tenantId + "','" + programId + "','M1')");
        jdbc.execute("INSERT INTO loyalty.auth_principal (principal_type, subject, display_name) VALUES ('USER','operator','Operator') ON CONFLICT DO NOTHING");
        jdbc.execute("INSERT INTO loyalty.auth_principal_role (principal_id, role_id, scope_type, tenant_id, program_id, status) " +
                "SELECT p.id, r.id, 'PROGRAM', '" + tenantId + "', '" + programId + "', 'ACTIVE' " +
                "FROM loyalty.auth_principal p, loyalty.auth_role r WHERE p.subject='operator' AND r.code='PROGRAM_ADMIN' ON CONFLICT DO NOTHING");
    }

    @AfterAll
    static void teardown() throws Exception { if (EmbeddedPgConfig.pg != null) EmbeddedPgConfig.pg.close(); }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor operator() {
        return jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()));
    }

    private String base(UUID acct) {
        return "/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + acct;
    }

    private UUID newAccount(MockMvc mvc, ObjectMapper json, String no) throws Exception {
        String resp = mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("accountNo", no, "accountType", "LOYALTY"))).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(resp).get("id").asText());
    }

    private UUID earn(MockMvc mvc, ObjectMapper json, UUID acct, String key, String amount) throws Exception {
        String body = json.writeValueAsString(Map.of("pointTypeId", pointTypeId.toString(), "amount", amount,
                "source", Map.of("type", "ORDER", "id", "O" + key)));
        return UUID.fromString(json.readTree(mvc.perform(post(base(acct) + "/point-operations/earn")
                .header("Idempotency-Key", "op-earn:" + key).contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("ledgerId").asText());
    }

    private BigDecimal balance(MockMvc mvc, ObjectMapper json, UUID acct) throws Exception {
        return new BigDecimal(json.readTree(mvc.perform(get(base(acct) + "/balances?pointTypeId=" + pointTypeId).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("balances").get(0).get("available").asText());
    }

    @Test
    void reverseReducesAsset(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        UUID acct = newAccount(mvc, json, "AC-RV");
        UUID earnId = earn(mvc, json, acct, "R1", "100.00");
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json, acct)).isEqualByComparingTo("100.00");

        String body = json.writeValueAsString(Map.of("referenceLedgerId", earnId.toString(), "amount", "40.00", "reasonCode", "RETURN"));
        mvc.perform(post(base(acct) + "/point-operations/reverse")
                .header("Idempotency-Key", "op-rev:1").contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json, acct)).isEqualByComparingTo("60.00");

        String body2 = json.writeValueAsString(Map.of("referenceLedgerId", earnId.toString(), "amount", "100.00", "reasonCode", "RETURN"));
        mvc.perform(post(base(acct) + "/point-operations/reverse")
                .header("Idempotency-Key", "op-rev:2").contentType(MediaType.APPLICATION_JSON).content(body2).with(operator()))
                .andExpect(status().isConflict());
    }

    @Test
    void restoreRestoresConsumed(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        UUID acct = newAccount(mvc, json, "AC-RS");
        earn(mvc, json, acct, "RS1", "200.00");
        String redeemBody = json.writeValueAsString(Map.of("pointTypeId", pointTypeId.toString(), "amount", "80.00",
                "source", Map.of("type", "ORDER", "id", "RR1")));
        UUID redeemLedger = UUID.fromString(json.readTree(mvc.perform(post(base(acct) + "/point-operations/redeem")
                .header("Idempotency-Key", "op-red:1").contentType(MediaType.APPLICATION_JSON).content(redeemBody).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("redeemLedgerId").asText());
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json, acct)).isEqualByComparingTo("120.00");

        String body = json.writeValueAsString(Map.of("redeemLedgerId", redeemLedger.toString(), "amount", "30.00", "reasonCode", "RETURN"));
        mvc.perform(post(base(acct) + "/point-operations/restore")
                .header("Idempotency-Key", "op-res:1").contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json, acct)).isEqualByComparingTo("150.00");

        String body2 = json.writeValueAsString(Map.of("redeemLedgerId", redeemLedger.toString(), "amount", "100.00", "reasonCode", "RETURN"));
        mvc.perform(post(base(acct) + "/point-operations/restore")
                .header("Idempotency-Key", "op-res:2").contentType(MediaType.APPLICATION_JSON).content(body2).with(operator()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void adjustPositiveAndNegative(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        UUID acct = newAccount(mvc, json, "AC-AD");
        String body = json.writeValueAsString(Map.of("pointTypeId", pointTypeId.toString(), "amount", "50.00",
                "reasonCode", "OPS", "source", Map.of("type", "CASE", "id", "C1")));
        mvc.perform(post(base(acct) + "/point-operations/adjust")
                .header("Idempotency-Key", "op-adj:1").contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json, acct)).isEqualByComparingTo("50.00");

        String body2 = json.writeValueAsString(Map.of("pointTypeId", pointTypeId.toString(), "amount", "-20.00",
                "reasonCode", "OPS", "source", Map.of("type", "CASE", "id", "C2")));
        mvc.perform(post(base(acct) + "/point-operations/adjust")
                .header("Idempotency-Key", "op-adj:2").contentType(MediaType.APPLICATION_JSON).content(body2).with(operator()))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json, acct)).isEqualByComparingTo("30.00");
    }
}

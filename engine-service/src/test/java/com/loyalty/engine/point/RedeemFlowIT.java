package com.loyalty.engine.point;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** M4 Redeem: FEFO cross-asset allocation, insufficient balance, idempotency, concurrency. */
@SpringBootTest(classes = EngineServiceApplication.class)
@AutoConfigureMockMvc
@Import(RedeemFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedeemFlowIT {

    private static UUID tenantId;
    private static UUID programId;
    private static UUID memberId;
    private static UUID pointTypeId;
    private static UUID accountId;

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
        EmbeddedPostgres embeddedPostgres() throws Exception {
            pg = EmbeddedPostgres.builder().start();
            return pg;
        }

        @Bean
        @Primary
        DataSource dataSource(EmbeddedPostgres pg) {
            return pg.getPostgresDatabase();
        }
    }

    @BeforeAll
    void seed(@Autowired DataSource ds) {
        tenantId = UUID.randomUUID();
        programId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        pointTypeId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.point_type (id, tenant_id, program_id, code, name, redeemable, tier_calculable, consumption_policy) " +
                "VALUES ('" + pointTypeId + "','" + tenantId + "','" + programId + "','BASIC','Basic',true,true,'FEFO')");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberId + "','" + tenantId + "','" + programId + "','M1')");
        jdbc.execute("INSERT INTO loyalty.account (id, tenant_id, program_id, member_id, account_no, account_type) " +
                "VALUES ('" + accountId + "','" + tenantId + "','" + programId + "','" + memberId + "','ACC','LOYALTY')");
        jdbc.execute("INSERT INTO loyalty.auth_principal (principal_type, subject, display_name) VALUES ('USER','operator','Operator') ON CONFLICT DO NOTHING");
        jdbc.execute("INSERT INTO loyalty.auth_principal_role (principal_id, role_id, scope_type, tenant_id, program_id, status) " +
                "SELECT p.id, r.id, 'PROGRAM', '" + tenantId + "', '" + programId + "', 'ACTIVE' " +
                "FROM loyalty.auth_principal p, loyalty.auth_role r " +
                "WHERE p.subject='operator' AND r.code='PROGRAM_ADMIN' ON CONFLICT DO NOTHING");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (EmbeddedPgConfig.pg != null) {
            EmbeddedPgConfig.pg.close();
        }
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor operator() {
        return jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()));
    }

    private String base() {
        return "/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + accountId;
    }

    private String earn(@Autowired MockMvc mvc, @Autowired ObjectMapper json, String key, String amount,
                        Instant expireAt) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "pointTypeId", pointTypeId.toString(), "amount", amount,
                "source", Map.of("type", "ORDER", "id", "O" + key),
                "expireAt", expireAt.toString()));
        return json.readTree(mvc.perform(post(base() + "/point-operations/earn")
                        .header("Idempotency-Key", "earn:" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("ledgerId").asText();
    }

    private JsonNode redeem(@Autowired MockMvc mvc, @Autowired ObjectMapper json, String key, String amount) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "pointTypeId", pointTypeId.toString(), "amount", amount,
                "source", Map.of("type", "ORDER", "id", "R" + key)));
        return json.readTree(mvc.perform(post(base() + "/point-operations/redeem")
                        .header("Idempotency-Key", "redeem:" + key)
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andReturn().getResponse().getContentAsString());
    }

    private BigDecimal balance(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        String resp = mvc.perform(get(base() + "/balances?pointTypeId=" + pointTypeId).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new BigDecimal(json.readTree(resp).get("balances").get(0).get("available").asText());
    }

    @Test
    void fefoCrossAssetAllocation(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        Instant now = Instant.now();
        String a = earn(mvc, json, "A", "100.00", now.plus(Duration.ofDays(10)));  // earliest expiry
        String b = earn(mvc, json, "B", "200.00", now.plus(Duration.ofDays(20)));
        String c = earn(mvc, json, "C", "300.00", now.plus(Duration.ofDays(30)));  // or null; here latest

        // Redeem 50 -> only from A (earliest expiry).
        JsonNode r1 = redeem(mvc, json, "1", "50.00");
        assertThatAllocationsAre(r1, new String[]{a}, new String[]{"50.00"});
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json)).isEqualByComparingTo("550.00");

        // Redeem 150 -> A remaining 50 + B 100.
        JsonNode r2 = redeem(mvc, json, "2", "150.00");
        assertThatAllocationsAre(r2, new String[]{a, b}, new String[]{"50.00", "100.00"});
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json)).isEqualByComparingTo("400.00");

        // Redeem 400 -> B remaining 100 + C 300.
        JsonNode r3 = redeem(mvc, json, "3", "400.00");
        assertThatAllocationsAre(r3, new String[]{b, c}, new String[]{"100.00", "300.00"});
        org.assertj.core.api.Assertions.assertThat(balance(mvc, json)).isEqualByComparingTo("0.00");

        // Redeem 1 -> INSUFFICIENT_BALANCE.
        mvc.perform(post(base() + "/point-operations/redeem")
                        .header("Idempotency-Key", "redeem:4")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "pointTypeId", pointTypeId.toString(), "amount", "1.00",
                                "source", Map.of("type", "ORDER", "id", "R4")))).with(operator()))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentRedeemCannotOverspend(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        // Separate account via the account API to avoid cross-test interference.
        String acctResp = mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                                Map.of("accountNo", "C-CONC", "accountType", "LOYALTY"))).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID acct = UUID.fromString(json.readTree(acctResp).get("id").asText());
        earnTo(mvc, json, acct, "1", "1000.00");

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        Runnable redeem = () -> {
            try {
                start.await();
                String body = json.writeValueAsString(Map.of("pointTypeId", pointTypeId.toString(),
                        "amount", "700.00", "source", Map.of("type", "ORDER", "id", "CR")));
                int status = mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId
                                + "/accounts/" + acct + "/point-operations/redeem")
                        .header("Idempotency-Key", "conc:" + Thread.currentThread().getName())
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                        .andReturn().getResponse().getStatus();
                if (status == 200) ok.incrementAndGet();
                else if (status == 409) conflict.incrementAndGet();
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        };
        new Thread(redeem, "T-A").start();
        new Thread(redeem, "T-B").start();
        start.countDown();
        done.await();

        org.assertj.core.api.Assertions.assertThat(ok.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(conflict.get()).isEqualTo(1);
        // Exactly 700 consumed from the 1000 earned.
        String bal = json.readTree(mvc.perform(get("/api/v1/programs/" + programId + "/members/" + memberId
                        + "/accounts/" + acct + "/balances?pointTypeId=" + pointTypeId).with(operator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("balances").get(0).get("available").asText();
        org.assertj.core.api.Assertions.assertThat(new BigDecimal(bal)).isEqualByComparingTo("300.00");
    }

    private void earnTo(MockMvc mvc, ObjectMapper json, UUID acct, String key, String amount) throws Exception {
        String body = json.writeValueAsString(Map.of("pointTypeId", pointTypeId.toString(), "amount", amount,
                "source", Map.of("type", "ORDER", "id", "O" + key)));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + acct + "/point-operations/earn")
                .header("Idempotency-Key", "earn:" + key).contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk());
    }

    private void assertThatAllocationsAre(JsonNode response, String[] assetIds, String[] amounts) {
        JsonNode allocs = response.get("allocations");
        org.assertj.core.api.Assertions.assertThat(allocs.size()).isEqualTo(assetIds.length);
        for (int i = 0; i < assetIds.length; i++) {
            org.assertj.core.api.Assertions.assertThat(allocs.get(i).get("assetLedgerId").asText()).isEqualTo(assetIds[i]);
            org.assertj.core.api.Assertions.assertThat(allocs.get(i).get("amount").asText()).isEqualTo(amounts[i]);
        }
    }
}

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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5 Outbox/Inbox/Kafka (embedded PG + embedded Kafka). Earn writes an outbox row; the
 * publisher drains it to Kafka; the {@link PointEventConsumer} records it idempotently in
 * the inbox. Re-delivery of the same event is a no-op.
 */
@SpringBootTest(classes = EngineServiceApplication.class)
@AutoConfigureMockMvc
@EmbeddedKafka(topics = "loyalty.point.events", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(OutboxInboxFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
class OutboxInboxFlowIT {

    private static UUID tenantId, programId, memberId, pointTypeId, accountId;
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
        r.add("loyalty.outbox.enabled", () -> "true");
        r.add("loyalty.outbox.poll-interval-ms", () -> "200");
    }

    @TestConfiguration
    static class EmbeddedPgConfig {
        @Bean(destroyMethod = "close")
        EmbeddedPostgres embeddedPostgres() throws Exception {
            pg = EmbeddedPostgres.builder().start();
            return pg;
        }
        @Bean
        @Primary
        DataSource dataSource(EmbeddedPostgres pg) { return pg.getPostgresDatabase(); }
    }

    @BeforeAll
    void seed(@Autowired DataSource ds) {
        tenantId = UUID.randomUUID(); programId = UUID.randomUUID();
        memberId = UUID.randomUUID(); pointTypeId = UUID.randomUUID(); accountId = UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.point_type (id, tenant_id, program_id, code, name, redeemable, tier_calculable) " +
                "VALUES ('" + pointTypeId + "','" + tenantId + "','" + programId + "','BASIC','Basic',true,true)");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberId + "','" + tenantId + "','" + programId + "','M1')");
        jdbc.execute("INSERT INTO loyalty.account (id, tenant_id, program_id, member_id, account_no, account_type) " +
                "VALUES ('" + accountId + "','" + tenantId + "','" + programId + "','" + memberId + "','ACC','LOYALTY')");
        jdbc.execute("INSERT INTO loyalty.auth_principal (principal_type, subject, display_name) VALUES ('USER','operator','Operator') ON CONFLICT DO NOTHING");
        jdbc.execute("INSERT INTO loyalty.auth_principal_role (principal_id, role_id, scope_type, tenant_id, program_id, status) " +
                "SELECT p.id, r.id, 'PROGRAM', '" + tenantId + "', '" + programId + "', 'ACTIVE' " +
                "FROM loyalty.auth_principal p, loyalty.auth_role r WHERE p.subject='operator' AND r.code='PROGRAM_ADMIN' ON CONFLICT DO NOTHING");
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor operator() {
        return jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()));
    }

    @Test
    void outboxPublishedAndInboxIdempotent(@Autowired MockMvc mvc, @Autowired ObjectMapper json,
                                           @Autowired DataSource ds, @Autowired KafkaTemplate<String, String> kafka) throws Exception {
        // Earn -> outbox row written + published + consumed.
        String body = json.writeValueAsString(Map.of("pointTypeId", pointTypeId.toString(), "amount", "100.00",
                "source", Map.of("type", "ORDER", "id", "M5-1")));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + accountId
                        + "/point-operations/earn").header("Idempotency-Key", "m5:earn:1")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk());

        // Wait for the publisher -> Kafka -> consumer -> inbox PROCESSED.
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        boolean delivered = false;
        for (int i = 0; i < 100; i++) {
            Integer c = jdbc.queryForObject(
                    "SELECT count(*) FROM loyalty.inbox_event WHERE consumer_name=? AND status='PROCESSED'",
                    Integer.class, PointEventConsumer.CONSUMER_NAME);
            if (c != null && c >= 1) { delivered = true; break; }
            Thread.sleep(100);
        }
        org.assertj.core.api.Assertions.assertThat(delivered).as("event delivered to inbox").isTrue();

        // Re-deliver the same payload to Kafka -> consumer skips (already processed).
        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM loyalty.outbox_event WHERE event_type='loyalty.point.earned.v1' LIMIT 1",
                String.class);
        kafka.send("loyalty.point.events", "account/" + accountId, payload).get(5, TimeUnit.SECONDS);
        Thread.sleep(1000);

        Integer after = jdbc.queryForObject(
                "SELECT count(*) FROM loyalty.inbox_event WHERE consumer_name=?",
                Integer.class, PointEventConsumer.CONSUMER_NAME);
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(1);

        // Outbox row marked PUBLISHED.
        Integer published = jdbc.queryForObject(
                "SELECT count(*) FROM loyalty.outbox_event WHERE status='PUBLISHED'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(published).isGreaterThanOrEqualTo(1);
    }
}

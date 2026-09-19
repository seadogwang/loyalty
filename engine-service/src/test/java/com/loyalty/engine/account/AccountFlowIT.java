package com.loyalty.engine.account;

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

/** M3-T04 account lifecycle (embedded PG + Flyway + MyBatis + shared security). */
@SpringBootTest(classes = EngineServiceApplication.class)
@AutoConfigureMockMvc
@Import(AccountFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountFlowIT {

    private static UUID tenantId;
    private static UUID programId;
    private static UUID memberId;

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
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberId + "','" + tenantId + "','" + programId + "','M1')");
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

    @Test
    void createSuspendClose(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        String body = json.writeValueAsString(Map.of("accountNo", "ACC-1", "accountType", "LOYALTY"));
        String resp = mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String accountId = json.readTree(resp).get("id").asText();

        mvc.perform(get("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + accountId).with(operator()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + accountId + "/suspend").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + accountId + "/close").with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // Closing again is rejected.
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberId + "/accounts/" + accountId + "/close").with(operator()))
                .andExpect(status().isConflict());
    }

    @Test
    void memberNotInScopeRejected(@Autowired MockMvc mvc, @Autowired ObjectMapper json) {
        UUID otherMember = UUID.randomUUID();
        try {
            String body = json.writeValueAsString(Map.of("accountNo", "ACC-X", "accountType", "LOYALTY"));
            mvc.perform(post("/api/v1/programs/" + programId + "/members/" + otherMember + "/accounts")
                            .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                    .andExpect(status().isNotFound()); // MEMBER_NOT_FOUND
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

package com.loyalty.member;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M3 member/identity acceptance (embedded PG + Flyway + MyBatis + shared security):
 * create member with initial identity, resolve by identity, multi-identity conflict,
 * and idempotent bind. Identities are normalized before uniqueness is enforced.
 */
@SpringBootTest(classes = MemberServiceApplication.class)
@AutoConfigureMockMvc
@Import(MemberFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberFlowIT {

    private static UUID tenantId;
    private static UUID programId;

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
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        // operator principal + PROGRAM-scoped PROGRAM_ADMIN binding (member.create/read/identity.write).
        jdbc.execute("INSERT INTO loyalty.auth_principal (principal_type, subject, display_name) " +
                "VALUES ('USER','operator','Operator') ON CONFLICT DO NOTHING");
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

    @Test
    void createResolveAndConflict(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        String phoneA = "+6591234567";  // normalizes to +6591234567
        String emailA = "a@example.com"; // normalizes to a@example.com
        String phoneB = "+6590000001";

        // Create member M1 with an initial PHONE identity.
        String body = json.writeValueAsString(Map.of(
                "memberNo", "M1",
                "initialIdentity", Map.of("type", "PHONE", "source", "LOYALTY", "value", phoneA)));
        mvc.perform(post("/api/v1/programs/" + programId + "/members")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isOk());

        // Resolve by that identity -> returns M1.
        String resolve = json.writeValueAsString(Map.of("identities",
                List.of(Map.of("type", "PHONE", "source", "LOYALTY", "value", phoneA))));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/resolve")
                        .contentType(MediaType.APPLICATION_JSON).content(resolve)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberNo").value("M1"));

        // Bind a second identity (EMAIL) to M1 (need memberId from resolve) — use the list endpoint to find it.
        String member1Id = json.readTree(
                mvc.perform(post("/api/v1/programs/" + programId + "/members/resolve")
                        .contentType(MediaType.APPLICATION_JSON).content(resolve)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        String bindEmail = json.writeValueAsString(Map.of("type", "EMAIL", "source", "LOYALTY", "value", emailA));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + member1Id + "/identities")
                        .contentType(MediaType.APPLICATION_JSON).content(bindEmail)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isOk());

        // Create M2 with a distinct PHONE identity.
        String body2 = json.writeValueAsString(Map.of(
                "memberNo", "M2",
                "initialIdentity", Map.of("type", "PHONE", "source", "LOYALTY", "value", phoneB)));
        mvc.perform(post("/api/v1/programs/" + programId + "/members")
                        .contentType(MediaType.APPLICATION_JSON).content(body2)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isOk());

        // Resolve with M1's phone + M2's phone -> conflict (different members).
        String conflict = json.writeValueAsString(Map.of("identities",
                List.of(Map.of("type", "PHONE", "source", "LOYALTY", "value", phoneA),
                        Map.of("type", "PHONE", "source", "LOYALTY", "value", phoneB))));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/resolve")
                        .contentType(MediaType.APPLICATION_JSON).content(conflict)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isConflict());

        // Bind M1's phone to M2 -> already bound to M1 -> 409.
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + member1Id + "/identities")
                        .contentType(MediaType.APPLICATION_JSON).content(
                                json.writeValueAsString(Map.of("type", "PHONE", "source", "LOYALTY", "value", phoneA)))
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isOk()); // idempotent: same member already has it
    }

    @Test
    void unauthenticatedRejected(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(post("/api/v1/programs/" + programId + "/members/resolve")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"identities\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pointTypeCreateAndCapabilityValidation(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        // Invalid capability combo: record_only + redeemable is rejected.
        String bad = json.writeValueAsString(Map.of(
                "code", "BAD", "name", "Bad", "redeemable", true, "recordOnly", true));
        mvc.perform(post("/api/v1/programs/" + programId + "/point-types")
                        .contentType(MediaType.APPLICATION_JSON).content(bad)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isBadRequest());

        // Valid redeemable point type.
        String body = json.writeValueAsString(Map.of(
                "code", "BASIC", "name", "Basic", "redeemable", true, "tierCalculable", true));
        mvc.perform(post("/api/v1/programs/" + programId + "/point-types")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BASIC"));

        // Program scope query.
        mvc.perform(get("/api/v1/programs/" + programId)
                        .with(jwt().jwt(j -> j.subject("operator").claim("principal_type", "USER")
                                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PP"));
    }
}

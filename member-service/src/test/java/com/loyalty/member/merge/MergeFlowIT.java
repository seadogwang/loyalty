package com.loyalty.member.merge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.member.MemberServiceApplication;
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

/** M8 member merge: A->B, canonical resolve A->B, cycle B->A rejected. */
@SpringBootTest(classes = MemberServiceApplication.class)
@AutoConfigureMockMvc
@Import(MergeFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MergeFlowIT {

    private static UUID tenantId, programId, memberA, memberB;
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
        memberA = UUID.randomUUID(); memberB = UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberA + "','" + tenantId + "','" + programId + "','A')");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberB + "','" + tenantId + "','" + programId + "','B')");
        jdbc.execute("INSERT INTO loyalty.member_identity (id, tenant_id, program_id, member_id, identity_type, identity_source, identity_value, normalized_value, status) " +
                "VALUES (gen_random_uuid(),'" + tenantId + "','" + programId + "','" + memberA + "','PHONE','LOYALTY','+659111','+659111','ACTIVE')");
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

    @Test
    void mergeCanonicalResolveAndCycleRejection(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        // Merge A -> B.
        String body = json.writeValueAsString(Map.of("targetMemberId", memberB.toString(), "reason", "duplicate"));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberA + "/merge")
                .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MERGED"));

        // A's phone identity now resolves to B (canonical).
        String resolve = json.writeValueAsString(Map.of("identities",
                List.of(Map.of("type", "PHONE", "source", "LOYALTY", "value", "+659111"))));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/resolve")
                .contentType(MediaType.APPLICATION_JSON).content(resolve).with(operator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberB.toString()));

        // Cycle: merge B -> A is rejected (A's canonical is B).
        String body2 = json.writeValueAsString(Map.of("targetMemberId", memberA.toString(), "reason", "cycle"));
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberB + "/merge")
                .contentType(MediaType.APPLICATION_JSON).content(body2).with(operator()))
                .andExpect(status().isConflict());

        // Re-merging A (already merged) is rejected.
        mvc.perform(post("/api/v1/programs/" + programId + "/members/" + memberA + "/merge")
                .contentType(MediaType.APPLICATION_JSON).content(body).with(operator()))
                .andExpect(status().isConflict());
    }
}

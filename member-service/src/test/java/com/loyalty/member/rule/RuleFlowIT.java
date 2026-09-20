package com.loyalty.member.rule;

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
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** M6-T01 rule lifecycle (member-service): DRAFT -> PUBLISHED -> RETIRED; unique PUBLISHED. */
@SpringBootTest(classes = MemberServiceApplication.class)
@AutoConfigureMockMvc
@Import(RuleFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuleFlowIT {

    private static UUID tenantId, programId;
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
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.auth_principal (principal_type, subject, display_name) VALUES ('USER','author','Author') ON CONFLICT DO NOTHING");
        // RULE_AUTHOR has rule.version.write; publish needs RULE_PUBLISHER — give author both via a custom binding.
        jdbc.execute("INSERT INTO loyalty.auth_principal_role (principal_id, role_id, scope_type, status) " +
                "SELECT p.id, r.id, 'SYSTEM', 'ACTIVE' FROM loyalty.auth_principal p, loyalty.auth_role r " +
                "WHERE p.subject='author' AND r.code='PLATFORM_ADMIN' ON CONFLICT DO NOTHING");
    }

    @AfterAll
    static void teardown() { /* PG closed by context. */ }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor author() {
        return jwt().jwt(j -> j.subject("author").claim("principal_type", "USER")
                .claim("tenant_id", tenantId.toString()).claim("program_id", programId.toString()));
    }

    @Test
    void lifecycleUniquePublishedAndRollback(@Autowired MockMvc mvc, @Autowired ObjectMapper json) throws Exception {
        // Create definition.
        String defResp = mvc.perform(post("/api/v1/programs/" + programId + "/rules")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("code", "ORDER_EARN", "name", "Order Earn", "domain", "point"))).with(author()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID defId = UUID.fromString(json.readTree(defResp).get("id").asText());

        // Create + publish v1.
        String v1 = mvc.perform(post("/api/v1/programs/" + programId + "/rules/" + defId + "/versions")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("drl", "rule \"X\" when Object() then end", "createdBy", "author"))).with(author()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID v1Id = UUID.fromString(json.readTree(v1).get("id").asText());
        mvc.perform(post("/api/v1/programs/" + programId + "/rules/versions/" + v1Id + "/publish").with(author()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Create v2; publishing while v1 is PUBLISHED must fail (unique PUBLISHED).
        String v2 = mvc.perform(post("/api/v1/programs/" + programId + "/rules/" + defId + "/versions")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("drl", "rule \"Y\" when Object() then end", "createdBy", "author"))).with(author()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID v2Id = UUID.fromString(json.readTree(v2).get("id").asText());
        mvc.perform(post("/api/v1/programs/" + programId + "/rules/versions/" + v2Id + "/publish").with(author()))
                .andExpect(status().isConflict());

        // Rollback: retire v1, then publish v2.
        mvc.perform(post("/api/v1/programs/" + programId + "/rules/versions/" + v1Id + "/retire").with(author()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETIRED"));
        mvc.perform(post("/api/v1/programs/" + programId + "/rules/versions/" + v2Id + "/publish").with(author()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/programs/" + programId + "/rules/" + defId + "/published").with(author()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(2));
    }
}

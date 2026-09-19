package com.loyalty.access;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2 acceptance: requests are denied by default (401 without token, 403 for an
 * unmapped path or an insufficient principal); a SYSTEM-scoped principal holding
 * {@code role.binding.write} can read permissions. Uses embedded PostgreSQL + Flyway.
 */
@SpringBootTest(classes = AccessControlServiceApplication.class)
@AutoConfigureMockMvc
@Import(AuthorizationFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthorizationFlowIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // Symmetric key so the JwtDecoder builds without an IdP; the mock JWT bypasses it.
        r.add("loyalty.security.jwt.symmetric-key", () -> "test-secret-key-at-least-32-bytes-long-xx");
        r.add("loyalty.security.jwt.issuer-uri", () -> "https://idp.test/auth");
        r.add("loyalty.security.jwt.audience", () -> "loyalty-platform");
        // Use the embedded DataSource bean below; do not autoconfigure Hikari from app.yml.
        r.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
        // No Eureka registry running in unit tests.
        r.add("eureka.client.enabled", () -> "false");
        r.add("spring.cloud.discovery.enabled", () -> "false");
        // In-memory cache (no Redis needed in tests).
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
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // A managed principal + SYSTEM-scoped PLATFORM_ADMIN binding (PLATFORM_ADMIN is
        // seeded with all managed permissions, including role.binding.write).
        jdbc.execute("INSERT INTO loyalty.auth_principal (principal_type, subject, display_name) " +
                "VALUES ('USER','admin','Admin') ON CONFLICT DO NOTHING");
        jdbc.execute("INSERT INTO loyalty.auth_principal_role (principal_id, role_id, scope_type, status) " +
                "SELECT p.id, r.id, 'SYSTEM', 'ACTIVE' " +
                "FROM loyalty.auth_principal p, loyalty.auth_role r " +
                "WHERE p.subject='admin' AND r.code='PLATFORM_ADMIN' ON CONFLICT DO NOTHING");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (EmbeddedPgConfig.pg != null) {
            EmbeddedPgConfig.pg.close();
        }
    }

    @Test
    void noTokenUnauthenticated(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/api/v1/admin/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unmappedPathDenied(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/api/v1/nope").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void insufficientPermissionDenied(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/api/v1/admin/permissions")
                        .with(jwt().jwt(j -> j.subject("nobody").claim("principal_type", "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminWithPermissionAllowed(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(get("/api/v1/admin/permissions")
                        .with(jwt().jwt(j -> j.subject("admin").claim("principal_type", "USER"))))
                .andExpect(status().isOk());
    }
}

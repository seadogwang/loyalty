package com.loyalty.engine.benefit;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M7-T03 benefit grant + idempotent replay. */
@SpringBootTest(classes = EngineServiceApplication.class)
@Import(BenefitFlowIT.EmbeddedPgConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BenefitFlowIT {

    private static UUID tenantId, programId, schemeId, tierId, benefitId, memberId;
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
        schemeId = UUID.randomUUID(); tierId = UUID.randomUUID();
        benefitId = UUID.randomUUID(); memberId = UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("INSERT INTO loyalty.tenant (id, code, name) VALUES ('" + tenantId + "','TT','TT')");
        jdbc.execute("INSERT INTO loyalty.program (id, tenant_id, code, name) VALUES ('" + programId + "','" + tenantId + "','PP','PP')");
        jdbc.execute("INSERT INTO loyalty.tier_scheme (id, tenant_id, program_id, code, name, evaluation_period_type) " +
                "VALUES ('" + schemeId + "','" + tenantId + "','" + programId + "','MS','Membership','ROLLING')");
        jdbc.execute("INSERT INTO loyalty.tier (id, tenant_id, program_id, tier_scheme_id, code, name, rank_no) " +
                "VALUES ('" + tierId + "','" + tenantId + "','" + programId + "','" + schemeId + "','GOLD','Gold',2)");
        jdbc.execute("INSERT INTO loyalty.benefit (id, tenant_id, program_id, code, name, benefit_type) " +
                "VALUES ('" + benefitId + "','" + tenantId + "','" + programId + "','FREE_SHIP','Free Shipping','DISCOUNT')");
        jdbc.execute("INSERT INTO loyalty.tier_benefit_mapping (tenant_id, program_id, tier_id, benefit_id, effective_from) " +
                "VALUES ('" + tenantId + "','" + programId + "','" + tierId + "','" + benefitId + "',now())");
        jdbc.execute("INSERT INTO loyalty.member (id, tenant_id, program_id, member_no) VALUES ('" + memberId + "','" + tenantId + "','" + programId + "','M1')");
    }

    @AfterAll
    static void teardown() { /* PG closed by context. */ }

    @Test
    void grantAndIdempotentReplay(@Autowired BenefitService benefitService, @Autowired DataSource ds) {
        int granted = benefitService.grantForTier(tenantId, programId, memberId, tierId, "TIER", "tier-changed-1");
        assertThat(granted).isEqualTo(1);

        List<com.loyalty.engine.benefit.domain.MemberBenefit> active =
                benefitService.listActive(tenantId, programId, memberId);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).benefitId()).isEqualTo(benefitId);

        // Replay the same grant -> idempotent, no new benefit.
        int replayed = benefitService.grantForTier(tenantId, programId, memberId, tierId, "TIER", "tier-changed-1");
        assertThat(replayed).isZero();
        assertThat(benefitService.listActive(tenantId, programId, memberId)).hasSize(1);
    }
}

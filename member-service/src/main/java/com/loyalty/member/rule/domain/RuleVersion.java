package com.loyalty.member.rule.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Rule version (design 8.8 / 13.3). Lifecycle DRAFT -> TESTING -> PUBLISHED -> RETIRED;
 * at most one PUBLISHED per definition (enforced by partial unique index). The DRL source
 * is carried in {@code metadata} (jsonb, key {@code drl}) for V1; production wires an
 * artifact repository + checksum.
 */
public record RuleVersion(
        UUID id,
        UUID tenantId,
        UUID programId,
        UUID ruleDefinitionId,
        Integer versionNo,
        String status,
        Instant effectiveFrom,
        Instant effectiveTo,
        String artifactUri,
        String checksum,
        String metadata,
        String createdBy,
        Instant createdAt) {

    /** Lifecycle: DRAFT -> TESTING -> PUBLISHED -> RETIRED (design 13.3). */
    public boolean canTransitionTo(String target) {
        return switch (status) {
            case "DRAFT" -> target.equals("TESTING") || target.equals("PUBLISHED");
            case "TESTING" -> target.equals("PUBLISHED") || target.equals("DRAFT");
            case "PUBLISHED" -> target.equals("RETIRED");
            default -> false;
        };
    }
}

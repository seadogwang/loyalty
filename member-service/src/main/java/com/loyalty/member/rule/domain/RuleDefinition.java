package com.loyalty.member.rule.domain;

import java.time.Instant;
import java.util.UUID;

/** Rule definition (design 8.8): a named rule within a program + domain (point/tier/benefit). */
public record RuleDefinition(
        UUID id,
        UUID tenantId,
        UUID programId,
        String code,
        String name,
        String domain,
        String status,
        Instant createdAt) {
}

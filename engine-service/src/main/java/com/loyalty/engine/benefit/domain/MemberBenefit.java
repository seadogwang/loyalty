package com.loyalty.engine.benefit.domain;

import java.time.Instant;
import java.util.UUID;

/** A member's granted benefit instance (design 8.7). */
public record MemberBenefit(UUID id, UUID tenantId, UUID programId, UUID memberId,
                            UUID benefitId, String sourceType, String sourceId,
                            Instant effectiveFrom, Instant effectiveTo, String status) {
}

package com.loyalty.engine.tier.domain;

import java.time.Instant;
import java.util.UUID;

/** A member's tier assignment (design 8.6). Only one ACTIVE per (member, scheme). */
public record MemberTier(UUID id, UUID tenantId, UUID programId, UUID memberId,
                          UUID tierSchemeId, UUID tierId, String evaluationPeriodId,
                          Instant effectiveFrom, Instant effectiveTo, String status) {
}

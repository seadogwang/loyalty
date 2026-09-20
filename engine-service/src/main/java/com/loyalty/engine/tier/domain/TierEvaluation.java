package com.loyalty.engine.tier.domain;

import java.time.Instant;
import java.util.UUID;

/** A single tier evaluation (design 8.6). Idempotent on (tenant, program, member, scheme, period, idempotency_key). */
public record TierEvaluation(UUID id, UUID tenantId, UUID programId, UUID memberId,
                              UUID tierSchemeId, String evaluationPeriodId, Instant periodStart,
                              Instant periodEnd, UUID currentTierId, UUID targetTierId, String decision,
                              String transitionMode, UUID sourceEventId, String idempotencyKey,
                              Instant evaluatedAt) {
}

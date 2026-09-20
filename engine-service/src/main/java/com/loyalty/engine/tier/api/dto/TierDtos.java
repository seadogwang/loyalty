package com.loyalty.engine.tier.api.dto;

import java.time.Instant;
import java.util.UUID;

public final class TierDtos {
    private TierDtos() {}

    public record EvaluateRequest(UUID schemeId, UUID sourceEventId) {}
    public record TierResponse(UUID memberId, UUID schemeId, UUID tierId, String status, Instant effectiveFrom) {}
    public record EvaluationResponse(UUID evaluationId, String decision, UUID targetTierId, Instant evaluatedAt) {}
}

package com.loyalty.engine.tier.domain;

import java.util.UUID;

/** Read-only tier scheme view (engine reads tier_scheme from the shared DB). */
public record TierSchemeView(UUID id, UUID programId, String code, String evaluationPeriodType, String config) {
}

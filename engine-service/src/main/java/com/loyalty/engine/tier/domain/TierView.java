package com.loyalty.engine.tier.domain;

import java.util.UUID;

/** Read-only tier view. {@code config} (jsonb) carries the ranking threshold for V1
 *  (e.g. {@code {"threshold":1000}}). */
public record TierView(UUID id, UUID tierSchemeId, String code, int rankNo, String config) {
}

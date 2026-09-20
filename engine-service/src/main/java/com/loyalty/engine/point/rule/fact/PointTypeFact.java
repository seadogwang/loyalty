package com.loyalty.engine.point.rule.fact;

import java.util.UUID;

/** Point type fact (design 13.5). */
public record PointTypeFact(UUID pointTypeId, String code, boolean redeemable,
                           boolean tierCalculable, boolean recordOnly) {
}

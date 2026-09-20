package com.loyalty.engine.point.rule;

import java.math.BigDecimal;
import java.util.UUID;

/** A single point result from a Drools rule (design 13.6). */
public record PointResult(UUID pointTypeId, BigDecimal amount, String reason) {
}

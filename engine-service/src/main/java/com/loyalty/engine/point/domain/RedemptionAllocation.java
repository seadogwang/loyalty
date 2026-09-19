package com.loyalty.engine.point.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** A single consume allocation against an asset during a REDEEM (design 10.6). */
public record RedemptionAllocation(UUID assetLedgerId, BigDecimal amount) {
}

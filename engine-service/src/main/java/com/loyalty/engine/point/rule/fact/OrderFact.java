package com.loyalty.engine.point.rule.fact;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Order fact (design 13.5). */
public record OrderFact(String orderId, String channel, Instant orderTime,
                        BigDecimal totalNetAmount, String currency) {
}

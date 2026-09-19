package com.loyalty.engine.point.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A redeemable asset and its consumed/expired/reversed/adjusted/restored totals
 * (design 10.3). The remaining amount follows the unique formula:
 * {@code original - consumed - expired - reversed - adjustDebit + restored}.
 */
public record RedeemableAsset(
        UUID assetLedgerId,
        BigDecimal originalAmount,
        BigDecimal consumedAmount,
        BigDecimal expiredAmount,
        BigDecimal reversedAmount,
        BigDecimal adjustDebitAmount,
        BigDecimal restoredAmount,
        Instant effectiveAt,
        Instant expireAt) {

    public BigDecimal remainingAmount() {
        return originalAmount
                .subtract(consumedAmount)
                .subtract(expiredAmount)
                .subtract(reversedAmount)
                .subtract(adjustDebitAmount)
                .add(restoredAmount);
    }

    public boolean isExpired(Instant now) {
        return expireAt != null && !expireAt.isAfter(now);
    }
}

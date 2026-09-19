package com.loyalty.engine.point.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Aggregated allocation totals per asset ledger (design 10.4). */
public record AllocationAggregate(
        UUID assetLedgerId,
        BigDecimal consumed,
        BigDecimal expired,
        BigDecimal reversed,
        BigDecimal adjustDebit,
        BigDecimal restored) {

    public static final AllocationAggregate ZERO = new AllocationAggregate(
            null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public BigDecimal consumedOrZero() { return consumed == null ? BigDecimal.ZERO : consumed; }
    public BigDecimal expiredOrZero() { return expired == null ? BigDecimal.ZERO : expired; }
    public BigDecimal reversedOrZero() { return reversed == null ? BigDecimal.ZERO : reversed; }
    public BigDecimal adjustDebitOrZero() { return adjustDebit == null ? BigDecimal.ZERO : adjustDebit; }
    public BigDecimal restoredOrZero() { return restored == null ? BigDecimal.ZERO : restored; }
}

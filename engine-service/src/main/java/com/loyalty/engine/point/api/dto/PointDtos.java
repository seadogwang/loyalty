package com.loyalty.engine.point.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class PointDtos {
    private PointDtos() {}

    public record Source(String type, String id) {}

    public record EarnRequest(UUID pointTypeId, BigDecimal amount, Source source,
                              Instant effectiveAt, Instant expireAt) {}

    public record EarnResponse(UUID operationId, String status, UUID ledgerId,
                                UUID pointTypeId, BigDecimal amount) {}

    public record RedeemRequest(UUID pointTypeId, BigDecimal amount, Source source) {}

    public record RedeemAllocationItem(UUID assetLedgerId, String allocationType, BigDecimal amount) {}

    public record RedeemResponse(UUID operationId, String status, UUID redeemLedgerId,
                                  UUID pointTypeId, BigDecimal amount,
                                  java.util.List<RedeemAllocationItem> allocations) {}

    public record ReverseRequest(UUID referenceLedgerId, BigDecimal amount, String reasonCode) {}
    public record RestoreRequest(UUID redeemLedgerId, BigDecimal amount, String reasonCode) {}
    public record AdjustRequest(UUID pointTypeId, BigDecimal amount, String reasonCode, Source source) {}

    public record BalanceItem(UUID pointTypeId, BigDecimal available) {}

    public record BalanceResponse(UUID accountId, java.util.List<BalanceItem> balances) {}

    public record LedgerItem(UUID ledgerId, String transactionType, BigDecimal amount,
                             Instant effectiveAt, String sourceType, String sourceId,
                             Instant createdAt) {}
}

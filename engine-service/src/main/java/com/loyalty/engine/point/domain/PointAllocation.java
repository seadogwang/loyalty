package com.loyalty.engine.point.domain;

import com.loyalty.common.enums.AllocationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only allocation fact (design 10.3 / 8.5). {@code amount} is a positive number
 * describing how a positive asset is consumed/expired/reversed/restored/adjusted. The
 * {@code validate_point_allocation} trigger enforces allocation/transaction type match
 * and that the referenced asset is a positive redeemable asset.
 */
public record PointAllocation(
        UUID id,
        UUID tenantId,
        UUID programId,
        UUID memberId,
        UUID accountId,
        UUID pointTypeId,
        UUID transactionLedgerId,
        UUID assetLedgerId,
        UUID referenceAllocationId,
        AllocationType allocationType,
        BigDecimal amount,
        Instant createdAt) {
}

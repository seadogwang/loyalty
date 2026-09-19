package com.loyalty.member.config.domain;

import com.loyalty.common.enums.ConsumptionPolicy;

import java.time.Instant;
import java.util.UUID;

/** Point type configuration (design 4.8 / 8.4). Capability invariants enforced by DB CHECK
 *  and the service: {@code NOT (record_only AND redeemable)}. */
public record PointType(
        UUID id,
        UUID tenantId,
        UUID programId,
        String code,
        String name,
        boolean redeemable,
        boolean tierCalculable,
        boolean recordOnly,
        ConsumptionPolicy consumptionPolicy,
        String validityType,
        Integer validityPeriod,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}

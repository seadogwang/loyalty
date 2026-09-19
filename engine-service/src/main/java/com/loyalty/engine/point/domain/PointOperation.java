package com.loyalty.engine.point.domain;

import com.loyalty.common.enums.TransactionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency boundary for a point operation (design 23 / 8.5). The unique key
 * {@code (tenant_id, idempotency_key)} makes a repeat request return the stored result;
 * a different {@code request_hash} for the same key yields {@code IDEMPOTENCY_CONFLICT}.
 */
public record PointOperation(
        UUID id,
        UUID tenantId,
        UUID programId,
        UUID memberId,
        UUID accountId,
        UUID pointTypeId,
        TransactionType operationType,
        String idempotencyKey,
        String requestHash,
        String actorId,
        String reasonCode,
        String correlationId,
        String status,
        String responseJson,
        Instant createdAt,
        Instant updatedAt) {
}

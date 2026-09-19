package com.loyalty.engine.point.domain;

import java.time.Instant;
import java.util.UUID;

/** Outbox event row (design 8.9 / 21). Payload is the CloudEvents envelope JSON. */
public record OutboxEvent(
        UUID id,
        UUID tenantId,
        UUID programId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        String status,
        Instant createdAt,
        Instant publishedAt) {
}

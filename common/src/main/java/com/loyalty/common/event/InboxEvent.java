package com.loyalty.common.event;

import java.time.Instant;
import java.util.UUID;

/** Inbox idempotency row (design 8.9 / 22). PK = (consumer_name, event_id). */
public record InboxEvent(
        String consumerName,
        UUID eventId,
        UUID tenantId,
        UUID programId,
        String eventType,
        String status,
        Instant receivedAt,
        Instant processedAt,
        String errorMessage) {
}

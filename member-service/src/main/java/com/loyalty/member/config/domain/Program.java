package com.loyalty.member.config.domain;

import java.time.Instant;
import java.util.UUID;

/** Program aggregate (design 8.2): the loyalty root business boundary. */
public record Program(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String status,
        String timezone,
        Instant createdAt,
        Instant updatedAt) {
}

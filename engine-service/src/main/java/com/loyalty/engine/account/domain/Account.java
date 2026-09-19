package com.loyalty.engine.account.domain;

import com.loyalty.common.enums.AccountStatus;

import java.time.Instant;
import java.util.UUID;

/** Account aggregate (design 8.4): the point asset boundary. Owned by engine-service. */
public record Account(
        UUID id,
        UUID tenantId,
        UUID programId,
        UUID memberId,
        String accountNo,
        String accountType,
        AccountStatus status,
        Instant openedAt,
        Instant closedAt) {
}

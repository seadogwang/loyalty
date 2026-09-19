package com.loyalty.member.member.domain;

import com.loyalty.common.enums.MemberStatus;

import java.time.Instant;
import java.util.UUID;

/** Member aggregate (design 6.1 / 8.3). Belongs to exactly one Program within a Tenant. */
public record Member(
        UUID id,
        UUID tenantId,
        UUID programId,
        String memberNo,
        MemberStatus status,
        Instant joinedAt,
        UUID mergedToMemberId,
        Instant createdAt,
        Instant updatedAt) {
}

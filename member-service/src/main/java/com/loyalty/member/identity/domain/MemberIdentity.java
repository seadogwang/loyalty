package com.loyalty.member.identity.domain;

import com.loyalty.common.enums.IdentityStatus;
import com.loyalty.common.enums.IdentityType;

import java.time.Instant;
import java.util.UUID;

/** An identity bound to a member within a program (design 4.6 / 8.3). */
public record MemberIdentity(
        UUID id,
        UUID tenantId,
        UUID programId,
        UUID memberId,
        IdentityType identityType,
        String identitySource,
        String identityValue,
        String normalizedValue,
        boolean verified,
        boolean isPrimary,
        IdentityStatus status,
        Instant effectiveFrom,
        Instant effectiveTo,
        Instant createdAt,
        Instant updatedAt) {
}

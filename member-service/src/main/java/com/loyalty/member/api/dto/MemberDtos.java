package com.loyalty.member.api.dto;

import com.loyalty.common.enums.IdentityType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MemberDtos {
    private MemberDtos() {}

    public record IdentityRequest(IdentityType type, String source, String value,
                                   Boolean verified, Boolean isPrimary) {}

    public record ResolveRequest(List<IdentityRequest> identities) {}

    public record CreateMemberRequest(String memberNo, IdentityRequest initialIdentity) {}

    public record MemberResponse(UUID id, String memberNo, String status, UUID mergedToMemberId) {}

    public record IdentityResponse(UUID id, UUID memberId, IdentityType type, String source,
                                   String value, boolean verified, boolean isPrimary,
                                   String status, Instant effectiveFrom, Instant effectiveTo) {}

    public record ResolveResponse(String outcome, UUID memberId, String memberNo) {}
}

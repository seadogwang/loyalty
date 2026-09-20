package com.loyalty.member.api.dto;

import java.util.UUID;

public final class MergeDtos {
    private MergeDtos() {}

    public record MergeRequest(UUID targetMemberId, String reason) {}
    public record MergeResponse(UUID sourceMemberId, UUID targetMemberId, String status) {}
}

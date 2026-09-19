package com.loyalty.member.member;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.member.api.dto.MemberDtos;
import com.loyalty.member.identity.IdentityNormalizer;
import com.loyalty.member.identity.IdentityService;
import com.loyalty.member.member.domain.Member;
import com.loyalty.member.member.infrastructure.MemberMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Member lifecycle + multi-identity resolution (design 16.3 / M3-T02/T03). A merged
 * member is resolved to its canonical active target before returning.
 */
@Service
public class MemberService {

    private final MemberMapper memberMapper;
    private final IdentityService identityService;

    public MemberService(MemberMapper memberMapper, IdentityService identityService) {
        this.memberMapper = memberMapper;
        this.identityService = identityService;
    }

    /** Create a member and optionally bind an initial identity in the same transaction. */
    @Transactional
    public Member createMember(UUID programId, String memberNo, MemberDtos.IdentityRequest initialIdentity) {
        if (memberNo == null || memberNo.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "memberNo is required");
        }
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        UUID tenantId = ctx.tenantId();
        UUID id = UUID.randomUUID();
        try {
            memberMapper.insert(id, tenantId, programId, memberNo);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "member_no already exists in program");
        }
        if (initialIdentity != null && initialIdentity.type() != null) {
            identityService.bind(tenantId, programId, id, initialIdentity);
        }
        return memberMapper.findScoped(tenantId, programId, id);
    }

    /** Resolve a (possibly merged) member to its canonical active target. */
    public Member getMember(UUID programId, UUID memberId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return resolveCanonical(ctx.tenantId(), programId, memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND, "member not found"));
    }

    /**
     * Multi-identity resolution (design 16.3). Identities that map to different members
     * yield {@code IDENTITY_CONFLICT}; none found yields {@code MEMBER_NOT_FOUND}.
     * A merged member resolves to its canonical target.
     */
    public Member resolveMember(UUID programId, List<MemberDtos.IdentityRequest> identities) {
        if (identities == null || identities.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "identities required");
        }
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        Set<UUID> memberIds = new HashSet<>();
        for (MemberDtos.IdentityRequest req : identities) {
            String normalized = IdentityNormalizer.normalize(req.type(), req.value());
            UUID mid = identityService.resolveMemberId(programId, req.type(), req.source(), normalized);
            if (mid != null) {
                memberIds.add(mid);
            }
        }
        if (memberIds.isEmpty()) {
            throw new ApiException(ErrorCode.MEMBER_NOT_FOUND, "no identity matched a member");
        }
        if (memberIds.size() > 1) {
            throw new ApiException(ErrorCode.IDENTITY_CONFLICT, "identities point to different members");
        }
        UUID memberId = memberIds.iterator().next();
        return resolveCanonical(ctx.tenantId(), programId, memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND, "member not found"));
    }

    private java.util.Optional<Member> resolveCanonical(UUID tenantId, UUID programId, UUID memberId) {
        UUID current = memberId;
        for (int i = 0; i < 16; i++) {
            Member m = memberMapper.findScoped(tenantId, programId, current);
            if (m == null) {
                return java.util.Optional.empty();
            }
            if (m.status() != com.loyalty.common.enums.MemberStatus.MERGED || m.mergedToMemberId() == null) {
                return java.util.Optional.of(m);
            }
            current = m.mergedToMemberId();
        }
        return java.util.Optional.empty();
    }
}

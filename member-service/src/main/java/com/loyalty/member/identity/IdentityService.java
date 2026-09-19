package com.loyalty.member.identity;

import com.loyalty.common.enums.IdentityType;
import com.loyalty.member.api.dto.MemberDtos;
import com.loyalty.member.identity.domain.MemberIdentity;
import com.loyalty.member.identity.infrastructure.IdentityMapper;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Identity lifecycle: bind / replace / revoke (design 16.4–16.6). Normalizes values,
 * enforces active-identity uniqueness (translated to {@code IDENTITY_ALREADY_BOUND}), and
 * keeps bind idempotent when the identity already belongs to the same member.
 */
@Service
public class IdentityService {

    private final IdentityMapper identityMapper;

    public IdentityService(IdentityMapper identityMapper) {
        this.identityMapper = identityMapper;
    }

    @Transactional
    public MemberIdentity bind(UUID tenantId, UUID programId, UUID memberId, MemberDtos.IdentityRequest req) {
        String normalized = IdentityNormalizer.normalize(req.type(), req.value());
        MemberIdentity existing = identityMapper.findActiveByValue(programId,
                req.type().name(), req.source(), normalized);
        if (existing != null) {
            if (!existing.memberId().equals(memberId)) {
                throw new ApiException(ErrorCode.IDENTITY_ALREADY_BOUND,
                        "identity belongs to another member");
            }
            return existing; // idempotent
        }
        UUID id = UUID.randomUUID();
        identityMapper.insert(id, tenantId, programId, memberId, req.type().name(), req.source(),
                req.value(), normalized, bool(req.verified()), bool(req.isPrimary()));
        return identityMapper.findActiveByValue(programId, req.type().name(), req.source(), normalized);
    }

    @Transactional
    public void revoke(UUID programId, UUID memberId, UUID identityId) {
        MemberIdentity identity = loadScoped(programId, memberId, identityId);
        if (identity == null) {
            throw new ApiException(ErrorCode.IDENTITY_NOT_FOUND, "identity not found");
        }
        identityMapper.revoke(identityId, Instant.now());
    }

    @Transactional
    public MemberIdentity replace(UUID tenantId, UUID programId, UUID memberId, UUID identityId,
                                   MemberDtos.IdentityRequest req) {
        // Revoke the old identity then bind the new one in the same transaction.
        revoke(programId, memberId, identityId);
        return bind(tenantId, programId, memberId, req);
    }

    /** Resolve the active member (if any) that an identity maps to. */
    public UUID resolveMemberId(UUID programId, IdentityType type, String source, String normalized) {
        MemberIdentity identity = identityMapper.findActiveByValue(programId, type.name(), source, normalized);
        return identity == null ? null : identity.memberId();
    }

    public MemberIdentity loadScoped(UUID programId, UUID memberId, UUID identityId) {
        return identityMapper.findActiveByMember(null, programId, memberId).stream()
                .filter(i -> i.id().equals(identityId))
                .findFirst().orElse(null);
    }

    public java.util.List<MemberIdentity> list(UUID tenantId, UUID programId, UUID memberId) {
        return identityMapper.findActiveByMember(tenantId, programId, memberId);
    }

    private static boolean bool(Boolean b) {
        return Boolean.TRUE.equals(b);
    }
}

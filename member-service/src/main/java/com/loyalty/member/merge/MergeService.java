package com.loyalty.member.merge;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.enums.MemberStatus;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.member.member.domain.Member;
import com.loyalty.member.member.infrastructure.MemberMapper;
import com.loyalty.member.merge.infrastructure.MergeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Member merge (design 17 / M8-T02). Same tenant+program only; source must be ACTIVE;
 * cycle guard (A->B then B->A rejected) via canonical resolution. Identities transfer to
 * the target; history ledger/member_id is never rewritten; canonical resolution at query
 * time follows {@code merged_to_member_id}.
 */
@Service
public class MergeService {

    private final MemberMapper memberMapper;
    private final MergeMapper mergeMapper;

    public MergeService(MemberMapper memberMapper, MergeMapper mergeMapper) {
        this.memberMapper = memberMapper;
        this.mergeMapper = mergeMapper;
    }

    @Transactional
    public Member merge(UUID programId, UUID sourceMemberId, UUID targetMemberId, String reason, String operatorId) {
        if (sourceMemberId.equals(targetMemberId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "source and target must differ");
        }
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        Member source = requireActive(ctx.tenantId(), programId, sourceMemberId);
        Member target = requireActive(ctx.tenantId(), programId, targetMemberId);
        if (!source.programId().equals(target.programId()) || !source.programId().equals(programId)) {
            throw new ApiException(ErrorCode.CROSS_PROGRAM_MERGE_NOT_ALLOWED, "merge requires same program");
        }
        // Cycle guard: if target already resolves to source, merging source->target forms a cycle.
        Member canonical = resolveCanonical(ctx.tenantId(), programId, targetMemberId)
                .orElseThrow(() -> new ApiException(ErrorCode.MERGE_TARGET_INVALID, "target canonical unresolved"));
        if (canonical.id().equals(sourceMemberId)) {
            throw new ApiException(ErrorCode.MERGE_TARGET_INVALID, "merge would form a cycle");
        }

        // Lock order by UUID to avoid cross-merge deadlock (V1: process in sorted order).
        UUID first = sourceMemberId.compareTo(targetMemberId) <= 0 ? sourceMemberId : targetMemberId;
        UUID second = first.equals(sourceMemberId) ? targetMemberId : sourceMemberId;
        memberMapper.findScoped(ctx.tenantId(), programId, first);
        memberMapper.findScoped(ctx.tenantId(), programId, second);

        mergeMapper.transferIdentities(sourceMemberId, targetMemberId);
        memberMapper.setStatus(sourceMemberId, MemberStatus.MERGED.name(), targetMemberId);
        mergeMapper.insertMergeHistory(ctx.tenantId(), programId, sourceMemberId, targetMemberId, reason, operatorId);
        mergeMapper.insertCanonicalMapping(ctx.tenantId(), programId, sourceMemberId, targetMemberId);
        return memberMapper.findScoped(ctx.tenantId(), programId, sourceMemberId);
    }

    private Member requireActive(UUID tenantId, UUID programId, UUID memberId) {
        Member m = memberMapper.findScoped(tenantId, programId, memberId);
        if (m == null) throw new ApiException(ErrorCode.MEMBER_NOT_FOUND, "member not found");
        if (m.status() == MemberStatus.MERGED) {
            throw new ApiException(ErrorCode.MEMBER_ALREADY_MERGED, "source already merged");
        }
        return m;
    }

    private Optional<Member> resolveCanonical(UUID tenantId, UUID programId, UUID memberId) {
        UUID current = memberId;
        for (int i = 0; i < 16; i++) {
            Member m = memberMapper.findScoped(tenantId, programId, current);
            if (m == null) return Optional.empty();
            if (m.status() != MemberStatus.MERGED || m.mergedToMemberId() == null) return Optional.of(m);
            current = m.mergedToMemberId();
        }
        return Optional.empty();
    }
}

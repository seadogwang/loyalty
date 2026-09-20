package com.loyalty.engine.tier.infrastructure;

import com.loyalty.engine.tier.domain.MemberTier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface MemberTierMapper {
    MemberTier findActive(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                          @Param("memberId") UUID memberId, @Param("schemeId") UUID schemeId);

    int insert(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
              @Param("memberId") UUID memberId, @Param("schemeId") UUID schemeId, @Param("tierId") UUID tierId,
              @Param("evaluationPeriodId") String evaluationPeriodId, @Param("effectiveFrom") Instant effectiveFrom);

    int closeActive(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                   @Param("memberId") UUID memberId, @Param("schemeId") UUID schemeId, @Param("now") Instant now);

    List<MemberTier> findHistory(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                                @Param("memberId") UUID memberId, @Param("schemeId") UUID schemeId);

    List<MemberTier> findAllActiveByMember(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                                           @Param("memberId") UUID memberId);
}

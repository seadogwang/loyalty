package com.loyalty.engine.benefit.infrastructure;

import com.loyalty.engine.benefit.domain.MemberBenefit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface MemberBenefitMapper {
    int insert(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
              @Param("memberId") UUID memberId, @Param("benefitId") UUID benefitId,
              @Param("sourceType") String sourceType, @Param("sourceId") String sourceId);

    MemberBenefit findActiveByMemberAndBenefit(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                                               @Param("memberId") UUID memberId, @Param("benefitId") UUID benefitId);

    List<MemberBenefit> findActiveByMember(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                                          @Param("memberId") UUID memberId);
}

package com.loyalty.member.merge.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Member merge DAO (design 8.3 / 17): history + canonical mapping + identity transfer. */
@Mapper
public interface MergeMapper {
    int transferIdentities(@Param("sourceMemberId") UUID sourceMemberId, @Param("targetMemberId") UUID targetMemberId);

    int insertMergeHistory(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                          @Param("sourceMemberId") UUID sourceMemberId, @Param("targetMemberId") UUID targetMemberId,
                          @Param("reason") String reason, @Param("operatorId") String operatorId);

    int insertCanonicalMapping(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                              @Param("sourceMemberId") UUID sourceMemberId, @Param("targetMemberId") UUID targetMemberId);
}

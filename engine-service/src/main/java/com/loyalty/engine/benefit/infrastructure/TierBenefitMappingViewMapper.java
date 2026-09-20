package com.loyalty.engine.benefit.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/** Read-only tier->benefit mapping (engine reads tier_benefit_mapping from shared DB). */
@Mapper
public interface TierBenefitMappingViewMapper {
    List<UUID> findBenefitIdsByTier(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                                    @Param("tierId") UUID tierId);
}

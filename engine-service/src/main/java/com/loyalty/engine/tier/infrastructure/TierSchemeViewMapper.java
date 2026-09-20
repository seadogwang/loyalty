package com.loyalty.engine.tier.infrastructure;

import com.loyalty.engine.tier.domain.TierSchemeView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface TierSchemeViewMapper {
    TierSchemeView findScoped(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                              @Param("schemeId") UUID schemeId);
}

package com.loyalty.engine.tier.infrastructure;

import com.loyalty.engine.tier.domain.TierView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TierViewMapper {
    List<TierView> listByScheme(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                              @Param("schemeId") UUID schemeId);
    TierView findScoped(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                       @Param("id") UUID id);
}

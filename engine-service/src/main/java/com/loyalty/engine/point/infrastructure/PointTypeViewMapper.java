package com.loyalty.engine.point.infrastructure;

import com.loyalty.engine.point.domain.PointTypeView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Read-only point_type lookup for the engine (shared DB projection, design V1). */
@Mapper
public interface PointTypeViewMapper {

    PointTypeView findScoped(@Param("tenantId") UUID tenantId,
                             @Param("programId") UUID programId,
                             @Param("id") UUID id);
}

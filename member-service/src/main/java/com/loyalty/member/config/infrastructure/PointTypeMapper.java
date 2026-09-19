package com.loyalty.member.config.infrastructure;

import com.loyalty.member.config.domain.PointType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/** Point type DAO (MyBatis). Scope-aware (tenant+program). */
@Mapper
public interface PointTypeMapper {

    int insert(@Param("id") UUID id,
               @Param("tenantId") UUID tenantId,
               @Param("programId") UUID programId,
               @Param("code") String code,
               @Param("name") String name,
               @Param("redeemable") boolean redeemable,
               @Param("tierCalculable") boolean tierCalculable,
               @Param("recordOnly") boolean recordOnly,
               @Param("consumptionPolicy") String consumptionPolicy,
               @Param("validityType") String validityType,
               @Param("validityPeriod") Integer validityPeriod);

    PointType findByIdScoped(@Param("tenantId") UUID tenantId,
                             @Param("programId") UUID programId,
                             @Param("id") UUID id);

    PointType findByCodeScoped(@Param("tenantId") UUID tenantId,
                              @Param("programId") UUID programId,
                              @Param("code") String code);

    List<PointType> listByProgram(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId);
}

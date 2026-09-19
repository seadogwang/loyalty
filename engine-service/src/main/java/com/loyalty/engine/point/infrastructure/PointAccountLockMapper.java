package com.loyalty.engine.point.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Account+point_type advisory lock DAO (design 22.3). Upsert then SELECT FOR UPDATE. */
@Mapper
public interface PointAccountLockMapper {

    int upsert(@Param("tenantId") UUID tenantId,
               @Param("programId") UUID programId,
               @Param("accountId") UUID accountId,
               @Param("pointTypeId") UUID pointTypeId);

    /** Locks the row FOR UPDATE; the version is bumped to mark the operation. */
    int lockForUpdate(@Param("tenantId") UUID tenantId,
                     @Param("programId") UUID programId,
                     @Param("accountId") UUID accountId,
                     @Param("pointTypeId") UUID pointTypeId);
}

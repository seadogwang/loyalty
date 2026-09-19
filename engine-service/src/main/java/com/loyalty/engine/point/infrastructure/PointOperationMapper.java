package com.loyalty.engine.point.infrastructure;

import com.loyalty.engine.point.domain.PointOperation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Point operation idempotency DAO (MyBatis). */
@Mapper
public interface PointOperationMapper {

    /** Idempotent insert; returns 0 if (tenant_id, idempotency_key) already exists. */
    int insert(@Param("id") UUID id,
               @Param("tenantId") UUID tenantId,
               @Param("programId") UUID programId,
               @Param("memberId") UUID memberId,
               @Param("accountId") UUID accountId,
               @Param("pointTypeId") UUID pointTypeId,
               @Param("operationType") String operationType,
               @Param("idempotencyKey") String idempotencyKey,
               @Param("requestHash") String requestHash,
               @Param("actorId") String actorId,
               @Param("reasonCode") String reasonCode,
               @Param("correlationId") String correlationId,
               @Param("status") String status);

    PointOperation findByIdempotencyKey(@Param("tenantId") UUID tenantId,
                                        @Param("idempotencyKey") String idempotencyKey);

    int complete(@Param("id") UUID id, @Param("responseJson") String responseJson);

    int markFailed(@Param("id") UUID id, @Param("responseJson") String responseJson);
}

package com.loyalty.engine.tier.infrastructure;

import com.loyalty.engine.tier.domain.TierEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface TierEvaluationMapper {
    /** Idempotent insert; returns 0 if (tenant, member, scheme, period, idempotency_key) already exists. */
    int insert(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
              @Param("memberId") UUID memberId, @Param("schemeId") UUID schemeId,
              @Param("evaluationPeriodId") String evaluationPeriodId, @Param("periodStart") java.time.Instant periodStart,
              @Param("periodEnd") java.time.Instant periodEnd, @Param("currentTierId") UUID currentTierId,
              @Param("targetTierId") UUID targetTierId, @Param("decision") String decision,
              @Param("transitionMode") String transitionMode, @Param("sourceEventId") UUID sourceEventId,
              @Param("idempotencyKey") String idempotencyKey);

    TierEvaluation findByIdempotency(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                                    @Param("memberId") UUID memberId, @Param("schemeId") UUID schemeId,
                                    @Param("evaluationPeriodId") String evaluationPeriodId,
                                    @Param("idempotencyKey") String idempotencyKey);

    List<TierEvaluation> listByMemberScheme(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                                            @Param("memberId") UUID memberId, @Param("schemeId") UUID schemeId);
}

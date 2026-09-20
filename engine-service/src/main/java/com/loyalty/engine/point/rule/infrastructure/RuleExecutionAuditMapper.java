package com.loyalty.engine.point.rule.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface RuleExecutionAuditMapper {
    int insert(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
               @Param("ruleDefinitionId") UUID ruleDefinitionId, @Param("ruleVersionId") UUID ruleVersionId,
               @Param("correlationId") String correlationId,
               @Param("inputSnapshot") String inputSnapshot, @Param("resultSnapshot") String resultSnapshot,
               @Param("status") String status, @Param("executionMs") Integer executionMs);
}

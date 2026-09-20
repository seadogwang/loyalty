package com.loyalty.engine.point.rule.infrastructure;

import com.loyalty.engine.point.rule.RuleVersionView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface RuleVersionViewMapper {
    RuleVersionView findPublishedByDefinition(@Param("tenantId") UUID tenantId,
                                             @Param("programId") UUID programId,
                                             @Param("ruleDefinitionId") UUID ruleDefinitionId);
}

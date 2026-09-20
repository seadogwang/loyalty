package com.loyalty.member.rule.infrastructure;

import com.loyalty.member.rule.domain.RuleVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface RuleVersionMapper {

    int insert(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
               @Param("ruleDefinitionId") UUID ruleDefinitionId, @Param("versionNo") Integer versionNo,
               @Param("status") String status, @Param("metadata") String metadata, @Param("createdBy") String createdBy);

    RuleVersion findByIdScoped(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                               @Param("id") UUID id);

    /** The single PUBLISHED version for a definition, or null. */
    RuleVersion findPublishedByDefinition(@Param("tenantId") UUID tenantId,
                                         @Param("programId") UUID programId,
                                         @Param("ruleDefinitionId") UUID ruleDefinitionId);

    List<RuleVersion> findByDefinition(@Param("tenantId") UUID tenantId,
                                       @Param("programId") UUID programId,
                                       @Param("ruleDefinitionId") UUID ruleDefinitionId);

    int updateStatus(@Param("id") UUID id, @Param("status") String status,
                     @Param("effectiveFrom") java.time.Instant effectiveFrom,
                     @Param("effectiveTo") java.time.Instant effectiveTo);
}

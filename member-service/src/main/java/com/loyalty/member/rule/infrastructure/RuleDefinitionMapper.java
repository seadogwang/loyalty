package com.loyalty.member.rule.infrastructure;

import com.loyalty.member.rule.domain.RuleDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface RuleDefinitionMapper {

    int insert(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
              @Param("code") String code, @Param("name") String name, @Param("domain") String domain);

    RuleDefinition findByIdScoped(@Param("tenantId") UUID tenantId,
                                  @Param("programId") UUID programId, @Param("id") UUID id);

    List<RuleDefinition> listByProgram(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId);
}

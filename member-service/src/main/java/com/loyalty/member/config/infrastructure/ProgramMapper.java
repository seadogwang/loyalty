package com.loyalty.member.config.infrastructure;

import com.loyalty.member.config.domain.Program;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Program DAO (MyBatis). Reads are scope-aware (tenant+program). */
@Mapper
public interface ProgramMapper {

    Program findScoped(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId);
}

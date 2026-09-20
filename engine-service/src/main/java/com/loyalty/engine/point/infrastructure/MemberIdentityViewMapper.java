package com.loyalty.engine.point.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Read-only member identity resolution (engine reads member_identity from shared DB). */
@Mapper
public interface MemberIdentityViewMapper {
    /** Active member id for (program, identity type, source, normalized value), or null. */
    UUID findMemberId(@Param("programId") UUID programId, @Param("type") String type,
                      @Param("source") String source, @Param("normalized") String normalized);
}

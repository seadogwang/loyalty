package com.loyalty.member.member.infrastructure;

import com.loyalty.member.member.domain.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Member DAO (MyBatis). Reads are scope-aware (tenant+program). MyBatis maps underscore
 * columns to camelCase fields via {@code mapUnderscoreToCamelCase}; the record canonical
 * constructor is matched by parameter name (compiled with {@code -parameters}).
 */
@Mapper
public interface MemberMapper {

    int insert(@Param("id") UUID id,
               @Param("tenantId") UUID tenantId,
               @Param("programId") UUID programId,
               @Param("memberNo") String memberNo);

    Member findScoped(@Param("tenantId") UUID tenantId,
                      @Param("programId") UUID programId,
                      @Param("id") UUID id);

    Member findByMemberNo(@Param("tenantId") UUID tenantId,
                          @Param("programId") UUID programId,
                          @Param("memberNo") String memberNo);

    int setStatus(@Param("id") UUID id,
                  @Param("status") String status,
                  @Param("mergedToMemberId") UUID mergedToMemberId);
}

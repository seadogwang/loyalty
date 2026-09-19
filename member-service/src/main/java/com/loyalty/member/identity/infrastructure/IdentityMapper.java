package com.loyalty.member.identity.infrastructure;

import com.loyalty.member.identity.domain.MemberIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Identity DAO (MyBatis). The active-identity unique index
 * ({@code program + type + source + normalized_value WHERE status='ACTIVE'}) enforces
 * that a given identity is bound to at most one member at a time (design 4.6).
 */
@Mapper
public interface IdentityMapper {

    /** Active identity matching the logical unique key, or null. */
    MemberIdentity findActiveByValue(@Param("programId") UUID programId,
                                     @Param("type") String type,
                                     @Param("source") String source,
                                     @Param("normalized") String normalized);

    /** All active identities for a member. */
    List<MemberIdentity> findActiveByMember(@Param("tenantId") UUID tenantId,
                                            @Param("programId") UUID programId,
                                            @Param("memberId") UUID memberId);

    int insert(@Param("id") UUID id,
               @Param("tenantId") UUID tenantId,
               @Param("programId") UUID programId,
               @Param("memberId") UUID memberId,
               @Param("type") String type,
               @Param("source") String source,
               @Param("value") String value,
               @Param("normalized") String normalized,
               @Param("verified") boolean verified,
               @Param("isPrimary") boolean isPrimary);

    int revoke(@Param("id") UUID id, @Param("now") Instant now);
}

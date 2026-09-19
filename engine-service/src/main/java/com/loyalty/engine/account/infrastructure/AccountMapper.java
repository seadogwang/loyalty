package com.loyalty.engine.account.infrastructure;

import com.loyalty.engine.account.domain.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

/** Account DAO (MyBatis). Scope-aware (tenant+program). The lock table is initialized on
 *  first point operation (safe upsert) per design 22.3. */
@Mapper
public interface AccountMapper {

    int insert(@Param("id") UUID id,
               @Param("tenantId") UUID tenantId,
               @Param("programId") UUID programId,
               @Param("memberId") UUID memberId,
               @Param("accountNo") String accountNo,
               @Param("accountType") String accountType);

    Account findByIdScoped(@Param("tenantId") UUID tenantId,
                           @Param("programId") UUID programId,
                           @Param("memberId") UUID memberId,
                           @Param("id") UUID id);

    boolean memberExistsScoped(@Param("tenantId") UUID tenantId,
                               @Param("programId") UUID programId,
                               @Param("memberId") UUID memberId);

    int setStatus(@Param("id") UUID id, @Param("status") String status, @Param("close") boolean close);

    /** Advisory row lock for point operations on (tenant, program, account, point_type). */
    int lockForUpdate(@Param("tenantId") UUID tenantId,
                     @Param("programId") UUID programId,
                     @Param("accountId") UUID accountId,
                     @Param("pointTypeId") UUID pointTypeId);
}

package com.loyalty.engine.point.infrastructure;

import com.loyalty.common.enums.SourceType;
import com.loyalty.engine.point.domain.PointLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Append-only point ledger DAO (MyBatis, design 8.5 / 10.4). Exposes INSERT and reads only;
 * no update/delete methods — the append-only trigger guards immutability at the DB.
 */
@Mapper
public interface PointLedgerMapper {

    int insert(PointLedger ledger);

    PointLedger findByIdScoped(@Param("tenantId") UUID tenantId, @Param("programId") UUID programId,
                              @Param("id") UUID id);

    /** Positive redeemable assets (EARN / positive ADJUST / positive RECALCULATE). */
    List<PointLedger> findRedeemableAssets(@Param("tenantId") UUID tenantId,
                                           @Param("programId") UUID programId,
                                           @Param("accountId") UUID accountId,
                                           @Param("pointTypeId") UUID pointTypeId);

    /** Ledgers for a (source_type, source_id) — used to locate original earning for reversal. */
    List<PointLedger> findBySourceScoped(@Param("tenantId") UUID tenantId,
                                        @Param("programId") UUID programId,
                                        @Param("sourceType") String sourceType,
                                        @Param("sourceId") String sourceId);

    /** Cursor-paginated ledger for an account+point_type (newest first). */
    List<PointLedger> findPage(@Param("tenantId") UUID tenantId,
                              @Param("programId") UUID programId,
                              @Param("accountId") UUID accountId,
                              @Param("pointTypeId") UUID pointTypeId,
                              @Param("from") Instant from,
                              @Param("cursorTime") Instant cursorTime,
                              @Param("cursorId") UUID cursorId,
                              @Param("limit") int limit);

    /** Ranking points for a member over a period (design 10.2): tier-calculable types,
     *  excluding RESTORE, by effective_at. */
    java.math.BigDecimal sumRankingPoints(@Param("tenantId") UUID tenantId,
                                          @Param("programId") UUID programId,
                                          @Param("memberId") UUID memberId,
                                          @Param("periodStart") Instant periodStart,
                                          @Param("periodEnd") Instant periodEnd);
}

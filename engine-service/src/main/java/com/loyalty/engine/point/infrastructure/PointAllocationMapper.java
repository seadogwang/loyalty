package com.loyalty.engine.point.infrastructure;

import com.loyalty.engine.point.domain.AllocationAggregate;
import com.loyalty.engine.point.domain.PointAllocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Append-only allocation DAO (MyBatis, design 8.5 / 10.4). INSERT + read only.
 * {@code sumByAssetLedgerIds} aggregates per-asset totals for balance calculation.
 */
@Mapper
public interface PointAllocationMapper {

    int insert(PointAllocation allocation);

    /** Aggregated allocation totals per asset ledger (FILTER per allocation_type). */
    List<AllocationAggregate> sumByAssetLedgerIds(@Param("tenantId") UUID tenantId,
                                                   @Param("programId") UUID programId,
                                                   @Param("accountId") UUID accountId,
                                                   @Param("pointTypeId") UUID pointTypeId,
                                                   @Param("assetLedgerIds") Collection<UUID> assetLedgerIds);

    /** Allocations whose transaction_ledger is the given ledger (e.g. a REDEEM's CONSUME rows). */
    List<PointAllocation> findByTransactionLedgerId(@Param("tenantId") UUID tenantId,
                                                     @Param("programId") UUID programId,
                                                     @Param("transactionLedgerId") UUID transactionLedgerId);

    /** Sum of RESTORE allocations referencing a given CONSUME allocation (for restore caps). */
    java.math.BigDecimal restoredForReference(@Param("tenantId") UUID tenantId,
                                              @Param("programId") UUID programId,
                                              @Param("referenceAllocationId") UUID referenceAllocationId);
}

package com.loyalty.engine.point;

import com.loyalty.engine.point.domain.AllocationAggregate;
import com.loyalty.engine.point.domain.PointLedger;
import com.loyalty.engine.point.domain.RedeemableAsset;
import com.loyalty.engine.point.infrastructure.PointAllocationMapper;
import com.loyalty.engine.point.infrastructure.PointLedgerMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Redeemable balance calculator (design 10.2 / 10.5) — the unique runtime formula:
 * <pre>
 *   asset_remaining = original - consumed - expired - reversed - adjustDebit + restored
 *   available = SUM(asset_remaining) where not expired and remaining > 0
 * </pre>
 * Balance is never persisted; it is recomputed from append-only Ledger + Allocation.
 */
@Service
public class BalanceService {

    private final PointLedgerMapper ledgerMapper;
    private final PointAllocationMapper allocationMapper;
    private final Clock clock;

    public BalanceService(PointLedgerMapper ledgerMapper, PointAllocationMapper allocationMapper, Clock clock) {
        this.ledgerMapper = ledgerMapper;
        this.allocationMapper = allocationMapper;
        this.clock = clock;
    }

    public BigDecimal calculateRedeemable(UUID tenantId, UUID programId, UUID accountId, UUID pointTypeId) {
        List<PointLedger> assets = ledgerMapper.findRedeemableAssets(tenantId, programId, accountId, pointTypeId);
        if (assets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<UUID> assetIds = assets.stream().map(PointLedger::id).toList();
        Map<UUID, AllocationAggregate> agg = allocationMapper
                .sumByAssetLedgerIds(tenantId, programId, accountId, pointTypeId, assetIds).stream()
                .collect(Collectors.toMap(AllocationAggregate::assetLedgerId, Function.identity()));
        java.time.Instant now = clock.instant();
        return assets.stream()
                .map(ledger -> toAsset(ledger, agg.get(ledger.id())))
                .filter(a -> !a.isExpired(now))
                .map(a -> a.remainingAmount().max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private RedeemableAsset toAsset(PointLedger ledger, AllocationAggregate agg) {
        if (agg == null) {
            agg = AllocationAggregate.ZERO;
        }
        return new RedeemableAsset(
                ledger.id(), ledger.amount(),
                agg.consumedOrZero(), agg.expiredOrZero(), agg.reversedOrZero(),
                agg.adjustDebitOrZero(), agg.restoredOrZero(),
                ledger.effectiveAt(), ledger.expireAt());
    }
}

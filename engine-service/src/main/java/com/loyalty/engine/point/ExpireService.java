package com.loyalty.engine.point;

import com.loyalty.common.enums.AllocationType;
import com.loyalty.common.enums.TransactionType;
import com.loyalty.common.event.EventEnvelope;
import com.loyalty.engine.point.domain.AllocationAggregate;
import com.loyalty.engine.point.domain.PointAllocation;
import com.loyalty.engine.point.domain.PointLedger;
import com.loyalty.engine.point.domain.RedeemableAsset;
import com.loyalty.engine.point.infrastructure.PointAccountLockMapper;
import com.loyalty.engine.point.infrastructure.PointAllocationMapper;
import com.loyalty.engine.point.infrastructure.PointLedgerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Expiration of redeemable assets past {@code expire_at} (design 10.8 / M4-T05). Each
 * expired asset with positive remaining gets an EXPIRE ledger (negative) + EXPIRE
 * allocation, under a per-asset deterministic idempotency key so re-runs are no-ops. The
 * account lock serializes against concurrent Redeem/Adjust.
 */
@Service
public class ExpireService {

    private static final Logger log = LoggerFactory.getLogger(ExpireService.class);

    private final IdempotencyService idempotency;
    private final PointLedgerMapper ledgerMapper;
    private final PointAllocationMapper allocationMapper;
    private final PointAccountLockMapper lockMapper;
    private final Clock clock;

    public ExpireService(IdempotencyService idempotency, PointLedgerMapper ledgerMapper,
                         PointAllocationMapper allocationMapper, PointAccountLockMapper lockMapper,
                         Clock clock) {
        this.idempotency = idempotency;
        this.ledgerMapper = ledgerMapper;
        this.allocationMapper = allocationMapper;
        this.lockMapper = lockMapper;
        this.clock = clock;
    }

    /** Expire all due assets for an account+point_type. Returns the number expired. */
    @Transactional
    public int expireDueAssets(UUID tenantId, UUID programId, UUID memberId, UUID accountId, UUID pointTypeId) {
        lockMapper.upsert(tenantId, programId, accountId, pointTypeId);
        lockMapper.lockForUpdate(tenantId, programId, accountId, pointTypeId);
        Instant now = clock.instant();

        List<PointLedger> assetLedgers = ledgerMapper.findRedeemableAssets(tenantId, programId, accountId, pointTypeId);
        if (assetLedgers.isEmpty()) return 0;
        Map<UUID, AllocationAggregate> agg = allocationMapper
                .sumByAssetLedgerIds(tenantId, programId, accountId, pointTypeId,
                        assetLedgers.stream().map(PointLedger::id).toList()).stream()
                .collect(Collectors.toMap(AllocationAggregate::assetLedgerId, Function.identity()));

        int expired = 0;
        for (PointLedger asset : assetLedgers) {
            if (asset.expireAt() == null || asset.expireAt().isAfter(now)) continue;
            AllocationAggregate a = agg.getOrDefault(asset.id(), AllocationAggregate.ZERO);
            BigDecimal remaining = asset.amount()
                    .subtract(a.consumedOrZero()).subtract(a.expiredOrZero())
                    .subtract(a.reversedOrZero()).subtract(a.adjustDebitOrZero())
                    .add(a.restoredOrZero());
            if (remaining.signum() <= 0) continue;
            if (expireOneAsset(tenantId, programId, memberId, accountId, pointTypeId, asset, remaining, now)) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expireOneAsset(UUID tenantId, UUID programId, UUID memberId, UUID accountId,
                                    UUID pointTypeId, PointLedger asset, BigDecimal remaining, Instant now) {
        String key = "expire:" + accountId + ":" + pointTypeId + ":" + asset.id();
        String requestHash = RequestHasher.hash(asset.id() + ":" + remaining.setScale(2));
        IdempotencyService.BeginResult begin;
        try {
            begin = idempotency.beginOperation(tenantId, programId, memberId, accountId, pointTypeId,
                    TransactionType.EXPIRE.name(), key, requestHash, "scheduler", "EXPIRE", null);
        } catch (com.loyalty.common.error.ApiException ex) {
            log.debug("expire skipped for asset {}: {}", asset.id(), ex.getMessage());
            return false;
        }
        if (begin.state() == IdempotencyService.State.COMPLETED) return false;

        UUID expireLedgerId = UUID.randomUUID();
        PointLedger expireLedger = new PointLedger(
                expireLedgerId, tenantId, programId, memberId, accountId, pointTypeId,
                TransactionType.EXPIRE, remaining.negate(), now, null,
                asset.sourceType(), asset.sourceId(),
                asset.id(), begin.operation().id(), null, null, null, null, "{}", null);
        ledgerMapper.insert(expireLedger);
        allocationMapper.insert(new PointAllocation(
                UUID.randomUUID(), tenantId, programId, memberId, accountId, pointTypeId,
                expireLedgerId, asset.id(), null, AllocationType.EXPIRE, remaining, null));
        idempotency.complete(begin.operation().id(), "{}");
        return true;
    }
}

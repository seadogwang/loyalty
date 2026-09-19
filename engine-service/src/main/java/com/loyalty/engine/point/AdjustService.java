package com.loyalty.engine.point;

import com.loyalty.common.enums.AccountStatus;
import com.loyalty.common.enums.AllocationType;
import com.loyalty.common.enums.TransactionType;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.common.event.EventEnvelope;
import com.loyalty.engine.account.AccountService;
import com.loyalty.engine.account.domain.Account;
import com.loyalty.engine.point.api.dto.PointDtos;
import com.loyalty.engine.point.domain.*;
import com.loyalty.engine.point.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Manual/system adjustment (design 10.11). A positive ADJUST creates a new redeemable
 * asset (amount > 0); a negative ADJUST consumes existing assets via ADJUST allocations
 * (FEFO/FIFO) — symmetric to Redeem but recorded as ADJUST for audit. Requires reason.
 */
@Service
public class AdjustService {

    private final IdempotencyService idempotency;
    private final PointLedgerMapper ledgerMapper;
    private final PointAllocationMapper allocationMapper;
    private final PointAccountLockMapper lockMapper;
    private final PointTypeViewMapper pointTypeViewMapper;
    private final AccountService accountService;
    private final AllocationPolicy allocationPolicy;
    private final OutboxService outbox;
    private final Clock clock;
    private final ObjectMapper json;

    public AdjustService(IdempotencyService idempotency, PointLedgerMapper ledgerMapper,
                         PointAllocationMapper allocationMapper, PointAccountLockMapper lockMapper,
                         PointTypeViewMapper pointTypeViewMapper, AccountService accountService,
                         AllocationPolicy allocationPolicy, OutboxService outbox, Clock clock,
                         ObjectMapper json) {
        this.idempotency = idempotency;
        this.ledgerMapper = ledgerMapper;
        this.allocationMapper = allocationMapper;
        this.lockMapper = lockMapper;
        this.pointTypeViewMapper = pointTypeViewMapper;
        this.accountService = accountService;
        this.allocationPolicy = allocationPolicy;
        this.outbox = outbox;
        this.clock = clock;
        this.json = json;
    }

    @Transactional
    public PointDtos.EarnResponse adjust(UUID tenantId, UUID programId, UUID memberId, UUID accountId,
                                          UUID pointTypeId, BigDecimal amount, String reasonCode,
                                          PointDtos.Source source, String idempotencyKey,
                                          String requestHash, String actorId, String correlationId) {
        if (amount == null || amount.signum() == 0) {
            throw new ApiException(ErrorCode.INVALID_ADJUSTMENT, "adjust amount must be non-zero");
        }
        IdempotencyService.BeginResult begin = idempotency.beginOperation(
                tenantId, programId, memberId, accountId, pointTypeId,
                TransactionType.ADJUST.name(), idempotencyKey, requestHash, actorId, reasonCode, correlationId);
        if (begin.state() == IdempotencyService.State.COMPLETED) {
            return readStored(begin.storedResponse());
        }
        Account account = accountService.get(programId, memberId, accountId);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "account is not ACTIVE");
        }
        Instant now = clock.instant();
        UUID ledgerId = UUID.randomUUID();

        if (amount.signum() > 0) {
            // Positive adjustment: a new redeemable asset (no allocation).
            PointLedger ledger = new PointLedger(
                    ledgerId, tenantId, programId, memberId, accountId, pointTypeId,
                    TransactionType.ADJUST, amount, now, null,
                    source != null && source.type() != null
                            ? com.loyalty.common.enums.SourceType.valueOf(source.type()) : null,
                    source != null ? source.id() : null,
                    null, begin.operation().id(), null, null, null, correlationId, "{}", null);
            ledgerMapper.insert(ledger);
            return finish(tenantId, programId, accountId, begin, ledgerId, pointTypeId, amount, correlationId);
        }

        // Negative adjustment: consume redeemable assets via ADJUST allocations.
        PointTypeView pt = pointTypeViewMapper.findScoped(tenantId, programId, pointTypeId);
        if (pt == null) {
            throw new ApiException(ErrorCode.POINT_TYPE_NOT_FOUND, "point type not found");
        }
        lockMapper.upsert(tenantId, programId, accountId, pointTypeId);
        lockMapper.lockForUpdate(tenantId, programId, accountId, pointTypeId);
        List<PointLedger> assetLedgers = ledgerMapper.findRedeemableAssets(tenantId, programId, accountId, pointTypeId);
        java.util.Map<UUID, AllocationAggregate> agg = assetLedgers.isEmpty() ? Map.of()
                : allocationMapper.sumByAssetLedgerIds(tenantId, programId, accountId, pointTypeId,
                        assetLedgers.stream().map(PointLedger::id).toList()).stream()
                        .collect(Collectors.toMap(AllocationAggregate::assetLedgerId, Function.identity()));
        List<RedeemableAsset> assets = assetLedgers.stream().map(l -> toAsset(l, agg.get(l.id()))).toList();
        List<RedemptionAllocation> allocs = allocationPolicy.allocate(assets, pt.consumptionPolicy(), amount.negate(), now);

        PointLedger ledger = new PointLedger(
                ledgerId, tenantId, programId, memberId, accountId, pointTypeId,
                TransactionType.ADJUST, amount, now, null,
                source != null && source.type() != null
                        ? com.loyalty.common.enums.SourceType.valueOf(source.type()) : null,
                source != null ? source.id() : null,
                null, begin.operation().id(), null, null, null, correlationId, "{}", null);
        ledgerMapper.insert(ledger);
        for (RedemptionAllocation a : allocs) {
            allocationMapper.insert(new PointAllocation(
                    UUID.randomUUID(), tenantId, programId, memberId, accountId, pointTypeId,
                    ledgerId, a.assetLedgerId(), null, AllocationType.ADJUST, a.amount(), null));
        }
        return finish(tenantId, programId, accountId, begin, ledgerId, pointTypeId, amount, correlationId);
    }

    private PointDtos.EarnResponse finish(UUID tenantId, UUID programId, UUID accountId,
                                         IdempotencyService.BeginResult begin, UUID ledgerId,
                                         UUID pointTypeId, BigDecimal amount, String correlationId) {
        PointDtos.EarnResponse response = new PointDtos.EarnResponse(
                begin.operation().id(), "COMPLETED", ledgerId, pointTypeId, amount);
        idempotency.complete(begin.operation().id(), write(response));
        outbox.publish(EventEnvelope.builder()
                .type("loyalty.point.adjusted.v1")
                .source("loyalty.engine-service").subject("account/" + accountId)
                .tenantId(tenantId).programId(programId).correlationId(correlationId)
                .data(Map.of("ledgerId", ledgerId.toString(), "amount", amount.setScale(2).toPlainString()))
                .build());
        return response;
    }

    private static RedeemableAsset toAsset(PointLedger ledger, AllocationAggregate agg) {
        if (agg == null) agg = AllocationAggregate.ZERO;
        return new RedeemableAsset(ledger.id(), ledger.amount(),
                agg.consumedOrZero(), agg.expiredOrZero(), agg.reversedOrZero(),
                agg.adjustDebitOrZero(), agg.restoredOrZero(),
                ledger.effectiveAt(), ledger.expireAt());
    }

    private PointDtos.EarnResponse readStored(String stored) {
        if (stored == null || stored.isBlank() || "{}".equals(stored)) {
            throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS, "stored response unavailable");
        }
        try { return json.readValue(stored, PointDtos.EarnResponse.class); }
        catch (Exception ex) { throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS, "stored response unreadable"); }
    }

    private String write(PointDtos.EarnResponse r) {
        try { return json.writeValueAsString(r); } catch (Exception ex) { return "{}"; }
    }
}

package com.loyalty.engine.point;

import com.loyalty.common.enums.AccountStatus;
import com.loyalty.common.enums.AllocationType;
import com.loyalty.common.enums.SourceType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Redeem application service (design 10.7). Account lock serializes concurrent redeems;
 * FEFO/FIFO allocation consumes the oldest/soonest-expiring assets first; a REDEEM ledger
 * (negative) and CONSUME allocations are written in the same transaction as the operation
 * record and outbox event.
 */
@Service
public class RedeemService {

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

    public RedeemService(IdempotencyService idempotency, PointLedgerMapper ledgerMapper,
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
    public PointDtos.RedeemResponse redeem(UUID tenantId, UUID programId, UUID memberId, UUID accountId,
                                           PointDtos.RedeemRequest req, String idempotencyKey,
                                           String requestHash, String actorId, String correlationId) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "amount must be positive");
        }
        IdempotencyService.BeginResult begin = idempotency.beginOperation(
                tenantId, programId, memberId, accountId, req.pointTypeId(),
                TransactionType.REDEEM.name(), idempotencyKey, requestHash, actorId, null, correlationId);
        if (begin.state() == IdempotencyService.State.COMPLETED) {
            return fromStored(begin.storedResponse());
        }

        Account account = accountService.get(programId, memberId, accountId);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "account is not ACTIVE");
        }
        PointTypeView pt = pointTypeViewMapper.findScoped(tenantId, programId, req.pointTypeId());
        if (pt == null) {
            throw new ApiException(ErrorCode.POINT_TYPE_NOT_FOUND, "point type not found");
        }
        if (!pt.redeemable()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "point type is not redeemable");
        }

        // Serialize concurrent point operations on this account+point_type.
        lockMapper.upsert(tenantId, programId, accountId, req.pointTypeId());
        lockMapper.lockForUpdate(tenantId, programId, accountId, req.pointTypeId());

        List<PointLedger> assetLedgers = ledgerMapper.findRedeemableAssets(tenantId, programId, accountId, req.pointTypeId());
        Map<UUID, AllocationAggregate> agg = assetLedgers.isEmpty() ? Map.of()
                : allocationMapper.sumByAssetLedgerIds(tenantId, programId, accountId, req.pointTypeId(),
                        assetLedgers.stream().map(PointLedger::id).toList()).stream()
                        .collect(Collectors.toMap(AllocationAggregate::assetLedgerId, Function.identity()));
        Instant now = clock.instant();
        List<RedeemableAsset> assets = assetLedgers.stream()
                .map(l -> toAsset(l, agg.get(l.id()))).toList();

        List<RedemptionAllocation> allocations = allocationPolicy.allocate(
                assets, pt.consumptionPolicy(), req.amount(), now);

        UUID redeemLedgerId = UUID.randomUUID();
        PointLedger redeemLedger = new PointLedger(
                redeemLedgerId, tenantId, programId, memberId, accountId, req.pointTypeId(),
                TransactionType.REDEEM, req.amount().negate(), now, null,
                req.source() != null && req.source().type() != null
                        ? SourceType.valueOf(req.source().type()) : null,
                req.source() != null ? req.source().id() : null,
                null, begin.operation().id(), null, null, null, correlationId, "{}", null);
        ledgerMapper.insert(redeemLedger);

        List<PointDtos.RedeemAllocationItem> allocationItems = new ArrayList<>();
        for (RedemptionAllocation a : allocations) {
            PointAllocation allocation = new PointAllocation(
                    UUID.randomUUID(), tenantId, programId, memberId, accountId, req.pointTypeId(),
                    redeemLedgerId, a.assetLedgerId(), null, AllocationType.CONSUME, a.amount(), null);
            allocationMapper.insert(allocation);
            allocationItems.add(new PointDtos.RedeemAllocationItem(
                    a.assetLedgerId(), AllocationType.CONSUME.name(), a.amount()));
        }

        PointDtos.RedeemResponse response = new PointDtos.RedeemResponse(
                begin.operation().id(), "COMPLETED", redeemLedgerId, req.pointTypeId(),
                req.amount().negate(), allocationItems);
        idempotency.complete(begin.operation().id(), write(response));

        outbox.publish(EventEnvelope.builder()
                .type("loyalty.point.redeemed.v1")
                .source("loyalty.engine-service")
                .subject("account/" + accountId)
                .tenantId(tenantId).programId(programId)
                .correlationId(correlationId)
                .data(Map.of("redeemLedgerId", redeemLedgerId.toString(),
                        "memberId", memberId.toString(),
                        "accountId", accountId.toString(),
                        "pointTypeId", req.pointTypeId().toString(),
                        "amount", req.amount().negate().setScale(2).toPlainString()))
                .build());
        return response;
    }

    private static RedeemableAsset toAsset(PointLedger ledger, AllocationAggregate agg) {
        if (agg == null) {
            agg = AllocationAggregate.ZERO;
        }
        return new RedeemableAsset(ledger.id(), ledger.amount(),
                agg.consumedOrZero(), agg.expiredOrZero(), agg.reversedOrZero(),
                agg.adjustDebitOrZero(), agg.restoredOrZero(),
                ledger.effectiveAt(), ledger.expireAt());
    }

    private PointDtos.RedeemResponse fromStored(String stored) {
        if (stored == null || stored.isBlank() || "{}".equals(stored)) {
            throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS, "stored response unavailable");
        }
        try {
            return json.readValue(stored, PointDtos.RedeemResponse.class);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS, "stored response unreadable");
        }
    }

    private String write(PointDtos.RedeemResponse response) {
        try {
            return json.writeValueAsString(response);
        } catch (Exception ex) {
            return "{}";
        }
    }
}

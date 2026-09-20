package com.loyalty.engine.order;

import com.loyalty.common.enums.AccountStatus;
import com.loyalty.common.enums.AllocationType;
import com.loyalty.common.enums.TransactionType;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.common.event.EventEnvelope;
import com.loyalty.engine.account.AccountService;
import com.loyalty.engine.account.domain.Account;
import com.loyalty.engine.order.api.dto.OrderDtos;
import com.loyalty.engine.point.IdempotencyService;
import com.loyalty.engine.point.OutboxService;
import com.loyalty.engine.point.RequestHasher;
import com.loyalty.engine.point.AllocationPolicy;
import com.loyalty.engine.point.domain.AllocationAggregate;
import com.loyalty.engine.point.domain.PointAllocation;
import com.loyalty.engine.point.domain.PointLedger;
import com.loyalty.engine.point.domain.RedeemableAsset;
import com.loyalty.engine.point.infrastructure.PointAccountLockMapper;
import com.loyalty.engine.point.infrastructure.PointAllocationMapper;
import com.loyalty.engine.point.infrastructure.PointLedgerMapper;
import com.loyalty.engine.point.infrastructure.PointTypeViewMapper;
import com.loyalty.engine.point.rule.RuleEngineService;
import com.loyalty.engine.point.rule.fact.OrderFact;
import com.loyalty.engine.point.rule.fact.PointTypeFact;
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
 * Recalculation (design 15 / M8). Re-runs the earn rule on the order facts to get the
 * expected amount, compares to the actual EARN ledger, and writes a RECALCULATE ledger
 * for the delta (positive = new asset; negative = RECALCULATE ledger + ADJUST allocations
 * consuming assets). History is never rewritten. Mode must be explicit (CURRENT_RULE for V1).
 */
@Service
public class RecalculateService {

    private final RuleEngineService ruleEngine;
    private final PointLedgerMapper ledgerMapper;
    private final PointAllocationMapper allocationMapper;
    private final PointAccountLockMapper lockMapper;
    private final PointTypeViewMapper pointTypeViewMapper;
    private final AccountService accountService;
    private final AllocationPolicy allocationPolicy;
    private final IdempotencyService idempotency;
    private final OutboxService outbox;
    private final Clock clock;

    public RecalculateService(RuleEngineService ruleEngine, PointLedgerMapper ledgerMapper,
                             PointAllocationMapper allocationMapper, PointAccountLockMapper lockMapper,
                             PointTypeViewMapper pointTypeViewMapper, AccountService accountService,
                             AllocationPolicy allocationPolicy, IdempotencyService idempotency,
                             OutboxService outbox, Clock clock) {
        this.ruleEngine = ruleEngine;
        this.ledgerMapper = ledgerMapper;
        this.allocationMapper = allocationMapper;
        this.lockMapper = lockMapper;
        this.pointTypeViewMapper = pointTypeViewMapper;
        this.accountService = accountService;
        this.allocationPolicy = allocationPolicy;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public OrderDtos.OrderResponse recalculate(UUID tenantId, UUID programId, OrderDtos.OrderRequest req,
                                                String mode, String correlationId) {
        if (!"CURRENT_RULE".equals(mode) && !"HISTORICAL_RULE".equals(mode)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "mode must be CURRENT_RULE or HISTORICAL_RULE");
        }
        String idempotencyKey = "recalc:" + req.orderId();
        String requestHash = RequestHasher.hash(idempotencyKey + ":" + mode);
        IdempotencyService.BeginResult begin = idempotency.beginOperation(
                tenantId, programId, req.memberId(), req.accountId(), req.pointTypeId(),
                TransactionType.RECALCULATE.name(), idempotencyKey, requestHash, "integration", "RECALC:" + mode, correlationId);
        if (begin.state() == IdempotencyService.State.COMPLETED) {
            return new OrderDtos.OrderResponse(req.orderId(), begin.operation().id(), null, "COMPLETED", BigDecimal.ZERO, null);
        }
        Account account = accountService.get(programId, req.memberId(), req.accountId());
        if (account.status() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "account is not ACTIVE");
        }
        var pt = pointTypeViewMapper.findScoped(tenantId, programId, req.pointTypeId());
        OrderFact order = new OrderFact(req.orderId(), req.channel(), Instant.now(), req.totalNetAmount(), req.currency());
        PointTypeFact ptFact = new PointTypeFact(req.pointTypeId(), "BASIC", pt.redeemable(), pt.tierCalculable(), pt.recordOnly());
        BigDecimal expected = ruleEngine.execute(tenantId, programId, req.ruleDefinitionId(), correlationId,
                List.of(order, ptFact)).getPointResults().stream()
                .map(r -> r.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = ledgerMapper.findBySourceScoped(tenantId, programId, "ORDER", req.orderId()).stream()
                .filter(l -> l.transactionType() == TransactionType.EARN)
                .map(PointLedger::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delta = expected.subtract(actual);
        if (delta.signum() == 0) {
            idempotency.complete(begin.operation().id(), "{}");
            return new OrderDtos.OrderResponse(req.orderId(), begin.operation().id(), null, "COMPLETED", BigDecimal.ZERO, null);
        }

        lockMapper.upsert(tenantId, programId, req.accountId(), req.pointTypeId());
        lockMapper.lockForUpdate(tenantId, programId, req.accountId(), req.pointTypeId());
        Instant now = clock.instant();
        UUID ledgerId = UUID.randomUUID();
        if (delta.signum() > 0) {
            // Positive delta = new redeemable asset.
            ledgerMapper.insert(new PointLedger(ledgerId, tenantId, programId, req.memberId(), req.accountId(),
                    req.pointTypeId(), TransactionType.RECALCULATE, delta, now, null,
                    com.loyalty.common.enums.SourceType.ORDER, req.orderId(), null, begin.operation().id(),
                    null, null, null, correlationId, "{}", null));
        } else {
            // Negative delta = consume assets via ADJUST allocations.
            List<PointLedger> assets = ledgerMapper.findRedeemableAssets(tenantId, programId, req.accountId(), req.pointTypeId());
            Map<UUID, AllocationAggregate> agg = assets.isEmpty() ? Map.of()
                    : allocationMapper.sumByAssetLedgerIds(tenantId, programId, req.accountId(), req.pointTypeId(),
                            assets.stream().map(PointLedger::id).toList()).stream()
                            .collect(Collectors.toMap(AllocationAggregate::assetLedgerId, Function.identity()));
            List<RedeemableAsset> pool = assets.stream().map(l -> toAsset(l, agg.get(l.id()))).toList();
            var allocs = allocationPolicy.allocate(pool, pt.consumptionPolicy(), delta.negate(), now);
            ledgerMapper.insert(new PointLedger(ledgerId, tenantId, programId, req.memberId(), req.accountId(),
                    req.pointTypeId(), TransactionType.RECALCULATE, delta, now, null,
                    com.loyalty.common.enums.SourceType.ORDER, req.orderId(), null, begin.operation().id(),
                    null, null, null, correlationId, "{}", null));
            for (var a : allocs) {
                allocationMapper.insert(new PointAllocation(UUID.randomUUID(), tenantId, programId, req.memberId(),
                        req.accountId(), req.pointTypeId(), ledgerId, a.assetLedgerId(), null, AllocationType.ADJUST, a.amount(), null));
            }
        }
        idempotency.complete(begin.operation().id(), "{}");
        outbox.publish(EventEnvelope.builder()
                .type("loyalty.point.recalculated.v1").source("loyalty.engine-service")
                .subject("account/" + req.accountId()).tenantId(tenantId).programId(programId).correlationId(correlationId)
                .data(Map.of("orderId", req.orderId(), "delta", delta.setScale(2).toPlainString(),
                        "mode", mode)).build());
        return new OrderDtos.OrderResponse(req.orderId(), begin.operation().id(), ledgerId, "COMPLETED", delta, null);
    }

    private static RedeemableAsset toAsset(PointLedger l, AllocationAggregate agg) {
        if (agg == null) agg = AllocationAggregate.ZERO;
        return new RedeemableAsset(l.id(), l.amount(), agg.consumedOrZero(), agg.expiredOrZero(),
                agg.reversedOrZero(), agg.adjustDebitOrZero(), agg.restoredOrZero(), l.effectiveAt(), l.expireAt());
    }
}

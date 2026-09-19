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
import com.loyalty.engine.point.domain.PointAllocation;
import com.loyalty.engine.point.domain.PointLedger;
import com.loyalty.engine.point.infrastructure.PointAllocationMapper;
import com.loyalty.engine.point.infrastructure.PointAccountLockMapper;
import com.loyalty.engine.point.infrastructure.PointLedgerMapper;
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

/**
 * Restore consumed points (design 10.9 / 10.10). Against a prior REDEEM ledger, restores
 * up to the original CONSUME amounts, creating a RESTORE ledger (positive) and RESTORE
 * allocations that reference the original CONSUME allocations. RESTORE does not increase
 * ranking (RANKING excludes RESTORE — handled in the tier service).
 */
@Service
public class RestoreService {

    private final IdempotencyService idempotency;
    private final PointLedgerMapper ledgerMapper;
    private final PointAllocationMapper allocationMapper;
    private final PointAccountLockMapper lockMapper;
    private final AccountService accountService;
    private final OutboxService outbox;
    private final Clock clock;
    private final ObjectMapper json;

    public RestoreService(IdempotencyService idempotency, PointLedgerMapper ledgerMapper,
                           PointAllocationMapper allocationMapper, PointAccountLockMapper lockMapper,
                           AccountService accountService, OutboxService outbox, Clock clock,
                           ObjectMapper json) {
        this.idempotency = idempotency;
        this.ledgerMapper = ledgerMapper;
        this.allocationMapper = allocationMapper;
        this.lockMapper = lockMapper;
        this.accountService = accountService;
        this.outbox = outbox;
        this.clock = clock;
        this.json = json;
    }

    @Transactional
    public PointDtos.EarnResponse restore(UUID tenantId, UUID programId, UUID memberId, UUID accountId,
                                           UUID redeemLedgerId, BigDecimal amount, String reasonCode,
                                           String idempotencyKey, String requestHash, String actorId,
                                           String correlationId) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "amount must be positive");
        }
        PointLedger redeemLedger = ledgerMapper.findByIdScoped(tenantId, programId, redeemLedgerId);
        if (redeemLedger == null || redeemLedger.transactionType() != TransactionType.REDEEM) {
            throw new ApiException(ErrorCode.NOT_FOUND, "redeem ledger not found");
        }
        IdempotencyService.BeginResult begin = idempotency.beginOperation(
                tenantId, programId, memberId, accountId, redeemLedger.pointTypeId(),
                TransactionType.RESTORE.name(), idempotencyKey, requestHash, actorId, reasonCode, correlationId);
        if (begin.state() == IdempotencyService.State.COMPLETED) {
            return readStored(begin.storedResponse());
        }
        Account account = accountService.get(programId, memberId, accountId);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "account is not ACTIVE");
        }
        lockMapper.upsert(tenantId, programId, accountId, redeemLedger.pointTypeId());
        lockMapper.lockForUpdate(tenantId, programId, accountId, redeemLedger.pointTypeId());

        // Phase 1: plan per-consume restore amounts; cap = consume - already-restored.
        List<PointAllocation> consumes = allocationMapper.findByTransactionLedgerId(
                tenantId, programId, redeemLedgerId).stream()
                .filter(a -> a.allocationType() == AllocationType.CONSUME).toList();
        record Plan(UUID assetLedgerId, UUID consumeAllocationId, BigDecimal restoreAmount) {}
        // First pass: total restorable across all consumes.
        BigDecimal totalRestorable = BigDecimal.ZERO;
        for (PointAllocation consume : consumes) {
            BigDecimal alreadyRestored = allocationMapper.restoredForReference(tenantId, programId, consume.id());
            BigDecimal restorable = consume.amount().subtract(alreadyRestored == null ? BigDecimal.ZERO : alreadyRestored);
            if (restorable.signum() > 0) totalRestorable = totalRestorable.add(restorable);
        }
        if (amount.compareTo(totalRestorable) > 0) {
            throw new ApiException(ErrorCode.RESTORE_NOT_ALLOWED, "restore amount exceeds restorable CONSUME");
        }

        List<Plan> plan = new ArrayList<>();
        BigDecimal remaining = amount;
        BigDecimal restoredTotal = BigDecimal.ZERO;
        for (PointAllocation consume : consumes) {
            if (remaining.signum() <= 0) break;
            BigDecimal alreadyRestored = allocationMapper.restoredForReference(tenantId, programId, consume.id());
            BigDecimal restorable = consume.amount().subtract(alreadyRestored == null ? BigDecimal.ZERO : alreadyRestored);
            if (restorable.signum() <= 0) continue;
            BigDecimal restoreAmt = restorable.min(remaining);
            plan.add(new Plan(consume.assetLedgerId(), consume.id(), restoreAmt));
            remaining = remaining.subtract(restoreAmt);
            restoredTotal = restoredTotal.add(restoreAmt);
        }
        if (restoredTotal.signum() <= 0) {
            throw new ApiException(ErrorCode.RESTORE_NOT_ALLOWED, "nothing restorable for this redeem");
        }

        // Phase 2: insert the RESTORE ledger first so the allocation trigger resolves transaction_ledger_id.
        Instant now = clock.instant();
        UUID restoreLedgerId = UUID.randomUUID();
        PointLedger restoreLedger = new PointLedger(
                restoreLedgerId, tenantId, programId, memberId, accountId, redeemLedger.pointTypeId(),
                TransactionType.RESTORE, restoredTotal, now, null,
                redeemLedger.sourceType(), redeemLedger.sourceId(),
                redeemLedger.id(), begin.operation().id(), null, null, null, correlationId, "{}", null);
        ledgerMapper.insert(restoreLedger);

        // Phase 3: insert RESTORE allocations referencing each original CONSUME.
        for (Plan p : plan) {
            allocationMapper.insert(new PointAllocation(
                    UUID.randomUUID(), tenantId, programId, memberId, accountId, redeemLedger.pointTypeId(),
                    restoreLedgerId, p.assetLedgerId(), p.consumeAllocationId(),
                    AllocationType.RESTORE, p.restoreAmount, null));
        }

        PointDtos.EarnResponse response = new PointDtos.EarnResponse(
                begin.operation().id(), "COMPLETED", restoreLedgerId, redeemLedger.pointTypeId(), restoredTotal);
        idempotency.complete(begin.operation().id(), write(response));

        outbox.publish(EventEnvelope.builder()
                .type("loyalty.point.restored.v1")
                .source("loyalty.engine-service").subject("account/" + accountId)
                .tenantId(tenantId).programId(programId).correlationId(correlationId)
                .data(Map.of("ledgerId", restoreLedgerId.toString(),
                        "redeemLedgerId", redeemLedger.id().toString(),
                        "amount", restoredTotal.setScale(2).toPlainString()))
                .build());
        return response;
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

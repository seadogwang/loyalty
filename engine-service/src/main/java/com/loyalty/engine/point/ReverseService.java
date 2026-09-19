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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reverse a prior positive asset (EARN / positive ADJUST / RECALCULATE) — design 10.9.
 * Writes a REVERSE ledger (negative) and a REVERSE allocation against the original asset;
 * the reversed amount cannot exceed the asset's current remaining. Idempotent + locked.
 */
@Service
public class ReverseService {

    private final IdempotencyService idempotency;
    private final PointLedgerMapper ledgerMapper;
    private final PointAllocationMapper allocationMapper;
    private final PointAccountLockMapper lockMapper;
    private final AccountService accountService;
    private final OutboxService outbox;
    private final Clock clock;
    private final ObjectMapper json;

    public ReverseService(IdempotencyService idempotency, PointLedgerMapper ledgerMapper,
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
    public PointDtos.EarnResponse reverse(UUID tenantId, UUID programId, UUID memberId, UUID accountId,
                                           UUID referenceLedgerId, BigDecimal amount, String reasonCode,
                                           String idempotencyKey, String requestHash, String actorId,
                                           String correlationId) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "amount must be positive");
        }
        PointLedger original = ledgerMapper.findByIdScoped(tenantId, programId, referenceLedgerId);
        if (original == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "reference ledger not found");
        }
        if (original.transactionType() != TransactionType.EARN
                && original.transactionType() != TransactionType.ADJUST
                && original.transactionType() != TransactionType.RECALCULATE) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "only positive assets can be reversed");
        }
        IdempotencyService.BeginResult begin = idempotency.beginOperation(
                tenantId, programId, memberId, accountId, original.pointTypeId(),
                TransactionType.REVERSE.name(), idempotencyKey, requestHash, actorId, reasonCode, correlationId);
        if (begin.state() == IdempotencyService.State.COMPLETED) {
            return readStored(begin.storedResponse());
        }

        Account account = accountService.get(programId, memberId, accountId);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "account is not ACTIVE");
        }
        lockMapper.upsert(tenantId, programId, accountId, original.pointTypeId());
        lockMapper.lockForUpdate(tenantId, programId, accountId, original.pointTypeId());

        BigDecimal reversible = currentRemaining(tenantId, programId, accountId, original);
        if (amount.compareTo(reversible) > 0) {
            throw new ApiException(ErrorCode.ALREADY_REVERSED, "reverse amount exceeds reversible remaining");
        }

        Instant now = clock.instant();
        UUID reverseLedgerId = UUID.randomUUID();
        PointLedger reverseLedger = new PointLedger(
                reverseLedgerId, tenantId, programId, memberId, accountId, original.pointTypeId(),
                TransactionType.REVERSE, amount.negate(), now, null,
                original.sourceType(), original.sourceId(),
                original.id(), begin.operation().id(), null, null, null, correlationId, "{}", null);
        ledgerMapper.insert(reverseLedger);

        PointAllocation allocation = new PointAllocation(
                UUID.randomUUID(), tenantId, programId, memberId, accountId, original.pointTypeId(),
                reverseLedgerId, original.id(), null, AllocationType.REVERSE, amount, null);
        allocationMapper.insert(allocation);

        PointDtos.EarnResponse response = new PointDtos.EarnResponse(
                begin.operation().id(), "COMPLETED", reverseLedgerId, original.pointTypeId(), amount.negate());
        idempotency.complete(begin.operation().id(), write(response));

        outbox.publish(EventEnvelope.builder()
                .type("loyalty.point.reversed.v1")
                .source("loyalty.engine-service")
                .subject("account/" + accountId)
                .tenantId(tenantId).programId(programId).correlationId(correlationId)
                .data(Map.of("ledgerId", reverseLedgerId.toString(),
                        "referenceLedgerId", original.id().toString(),
                        "amount", amount.negate().setScale(2).toPlainString()))
                .build());
        return response;
    }

    private BigDecimal currentRemaining(UUID tenantId, UUID programId, UUID accountId, PointLedger asset) {
        List<AllocationAggregate> aggs = allocationMapper.sumByAssetLedgerIds(
                tenantId, programId, accountId, asset.pointTypeId(), List.of(asset.id()));
        AllocationAggregate agg = aggs.isEmpty() ? AllocationAggregate.ZERO : aggs.get(0);
        return asset.amount()
                .subtract(agg.consumedOrZero()).subtract(agg.expiredOrZero())
                .subtract(agg.reversedOrZero()).subtract(agg.adjustDebitOrZero())
                .add(agg.restoredOrZero());
    }

    private PointDtos.EarnResponse readStored(String stored) {
        if (stored == null || stored.isBlank() || "{}".equals(stored)) {
            throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS, "stored response unavailable");
        }
        try {
            return json.readValue(stored, PointDtos.EarnResponse.class);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.OPERATION_IN_PROGRESS, "stored response unreadable");
        }
    }

    private String write(PointDtos.EarnResponse response) {
        try {
            return json.writeValueAsString(response);
        } catch (Exception ex) {
            return "{}";
        }
    }
}

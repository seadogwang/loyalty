package com.loyalty.engine.point;

import com.loyalty.common.enums.AccountStatus;
import com.loyalty.common.enums.SourceType;
import com.loyalty.common.enums.TransactionType;
import com.loyalty.common.event.EventEnvelope;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.engine.account.AccountService;
import com.loyalty.engine.account.domain.Account;
import com.loyalty.engine.point.api.dto.PointDtos;
import com.loyalty.engine.point.domain.PointLedger;
import com.loyalty.engine.point.infrastructure.PointLedgerMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Earn application service (design 14.1 / 10.x). Idempotent: a repeat with the same
 * Idempotency-Key returns the stored result; a different request body for the same key
 * yields {@code IDEMPOTENCY_CONFLICT}. Earn writes an EARN ledger (positive asset) and
 * an outbox event in one transaction. The account must be ACTIVE.
 */
@Service
public class EarnService {

    private final IdempotencyService idempotency;
    private final PointLedgerMapper ledgerMapper;
    private final AccountService accountService;
    private final OutboxService outbox;
    private final Clock clock;
    private final ObjectMapper json;

    public EarnService(IdempotencyService idempotency, PointLedgerMapper ledgerMapper,
                       AccountService accountService, OutboxService outbox, Clock clock,
                       ObjectMapper json) {
        this.idempotency = idempotency;
        this.ledgerMapper = ledgerMapper;
        this.accountService = accountService;
        this.outbox = outbox;
        this.clock = clock;
        this.json = json;
    }

    @Transactional
    public PointDtos.EarnResponse earn(UUID tenantId, UUID programId, UUID memberId, UUID accountId,
                                       PointDtos.EarnRequest req, String idempotencyKey,
                                       String requestHash, String actorId, String correlationId) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "amount must be positive");
        }
        IdempotencyService.BeginResult begin = idempotency.beginOperation(
                tenantId, programId, memberId, accountId, req.pointTypeId(),
                TransactionType.EARN.name(), idempotencyKey, requestHash, actorId, null, correlationId);
        if (begin.state() == IdempotencyService.State.COMPLETED) {
            return fromStored(begin.storedResponse());
        }

        Account account = accountService.get(programId, memberId, accountId);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "account is not ACTIVE");
        }

        Instant effectiveAt = req.effectiveAt() != null ? req.effectiveAt() : clock.instant();
        PointLedger ledger = new PointLedger(
                UUID.randomUUID(), tenantId, programId, memberId, accountId, req.pointTypeId(),
                TransactionType.EARN, req.amount(), effectiveAt, req.expireAt(),
                req.source() != null && req.source().type() != null
                        ? SourceType.valueOf(req.source().type()) : null,
                req.source() != null ? req.source().id() : null,
                null, begin.operation().id(), null, null, null, correlationId, "{}", null);
        ledgerMapper.insert(ledger);

        PointDtos.EarnResponse response = new PointDtos.EarnResponse(
                begin.operation().id(), "COMPLETED", ledger.id(), req.pointTypeId(), req.amount());
        idempotency.complete(begin.operation().id(), write(response));

        outbox.publish(EventEnvelope.builder()
                .type("loyalty.point.earned.v1")
                .source("loyalty.engine-service")
                .subject("account/" + accountId)
                .tenantId(tenantId).programId(programId)
                .correlationId(correlationId)
                .data(Map.of("ledgerId", ledger.id().toString(),
                        "memberId", memberId.toString(),
                        "accountId", accountId.toString(),
                        "pointTypeId", req.pointTypeId().toString(),
                        "amount", req.amount().setScale(2).toPlainString(),
                        "transactionType", "EARN"))
                .build());
        return response;
    }

    private PointDtos.EarnResponse fromStored(String stored) {
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

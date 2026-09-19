package com.loyalty.engine.point.domain;

import com.loyalty.common.enums.SourceType;
import com.loyalty.common.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only point ledger entry (design 10.3 / 8.5). {@code amount} is signed per
 * {@link TransactionType}; the DB CHECK {@code ck_point_ledger_amount_sign} and the
 * append-only trigger enforce immutability. This record is the INSERT shape only —
 * repositories expose no update/delete.
 */
public record PointLedger(
        UUID id,
        UUID tenantId,
        UUID programId,
        UUID memberId,
        UUID accountId,
        UUID pointTypeId,
        TransactionType transactionType,
        BigDecimal amount,
        Instant effectiveAt,
        Instant expireAt,
        SourceType sourceType,
        String sourceId,
        UUID referenceLedgerId,
        UUID operationId,
        UUID ruleId,
        UUID ruleVersionId,
        String ruleVersion,
        String correlationId,
        String calculationSnapshot,
        Instant createdAt) {
}

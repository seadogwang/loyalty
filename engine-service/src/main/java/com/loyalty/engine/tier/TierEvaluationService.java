package com.loyalty.engine.tier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.common.event.EventEnvelope;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.engine.point.OutboxService;
import com.loyalty.engine.point.infrastructure.PointLedgerMapper;
import com.loyalty.engine.tier.domain.*;
import com.loyalty.engine.tier.infrastructure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tier evaluation runtime (design 11 / M7-T01). Computes RANKING points over the
 * evaluation period, picks the target tier (DIRECT: highest tier whose threshold <= ranking),
 * and if the decision changes the member's tier, closes the old ACTIVE member_tier and
 * inserts exactly one new ACTIVE record — all in one transaction, with a deterministic
 * idempotency key. Emits {@code tier.changed.v1} only on change.
 */
@Service
public class TierEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(TierEvaluationService.class);

    private final TierSchemeViewMapper schemeMapper;
    private final TierViewMapper tierMapper;
    private final MemberTierMapper memberTierMapper;
    private final TierEvaluationMapper evalMapper;
    private final PointLedgerMapper ledgerMapper;
    private final OutboxService outbox;
    private final Clock clock;
    private final ObjectMapper json;

    public TierEvaluationService(TierSchemeViewMapper schemeMapper, TierViewMapper tierMapper,
                                 MemberTierMapper memberTierMapper, TierEvaluationMapper evalMapper,
                                 PointLedgerMapper ledgerMapper, OutboxService outbox, Clock clock,
                                 ObjectMapper json) {
        this.schemeMapper = schemeMapper;
        this.tierMapper = tierMapper;
        this.memberTierMapper = memberTierMapper;
        this.evalMapper = evalMapper;
        this.ledgerMapper = ledgerMapper;
        this.outbox = outbox;
        this.clock = clock;
        this.json = json;
    }

    @Transactional
    public TierEvaluation evaluate(UUID tenantId, UUID programId, UUID memberId, UUID schemeId,
                                    String correlationId, UUID sourceEventId) {
        return evaluate(tenantId, programId, memberId, schemeId, correlationId, sourceEventId, false);
    }

    @Transactional
    public TierEvaluation evaluate(UUID tenantId, UUID programId, UUID memberId, UUID schemeId,
                                    String correlationId, UUID sourceEventId, boolean forceRecalc) {
        TierSchemeView scheme = schemeMapper.findScoped(tenantId, programId, schemeId);
        if (scheme == null) throw new ApiException(ErrorCode.TIER_SCHEME_NOT_FOUND, "tier scheme not found");

        Instant now = clock.instant();
        Instant periodStart = now.minus(java.time.Duration.ofDays(365));
        Instant periodEnd = now;
        String periodId = "roll:" + LocalDate.now(clock);
        String idempotencyKey = forceRecalc ? "recalc:" + UUID.randomUUID()
                : sourceEventId != null ? "evt:" + sourceEventId : "manual:" + periodId;

        // Idempotency: if already evaluated for this key, return the prior result (no re-transition).
        TierEvaluation existing = evalMapper.findByIdempotency(tenantId, programId, memberId, schemeId, periodId, idempotencyKey);
        if (existing != null) return existing;

        BigDecimal ranking = ledgerMapper.sumRankingPoints(tenantId, programId, memberId, periodStart, periodEnd);
        List<TierView> tiers = tierMapper.listByScheme(tenantId, programId, schemeId);
        if (tiers.isEmpty()) throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "no tiers configured");

        MemberTier current = memberTierMapper.findActive(tenantId, programId, memberId, schemeId);
        UUID currentTierId = current != null ? current.tierId() : null;

        // DIRECT: highest tier whose threshold <= ranking.
        TierView target = null;
        for (TierView t : tiers) {
            BigDecimal threshold = threshold(t);
            if (ranking.compareTo(threshold) >= 0) target = t;
        }
        if (target == null) target = tiers.get(0);

        String decision = decide(current, tiers, target);
        UUID targetTierId = target.id();

        TierEvaluation eval = new TierEvaluation(UUID.randomUUID(), tenantId, programId, memberId, schemeId,
                periodId, periodStart, periodEnd, currentTierId, targetTierId, decision, "DIRECT",
                sourceEventId, idempotencyKey, now);
        int inserted = evalMapper.insert(eval.id(), tenantId, programId, memberId, schemeId, periodId,
                periodStart, periodEnd, currentTierId, targetTierId, decision, "DIRECT", sourceEventId, idempotencyKey);
        if (inserted == 0) {
            // concurrent eval — return the persisted one.
            return evalMapper.findByIdempotency(tenantId, programId, memberId, schemeId, periodId, idempotencyKey);
        }

        if ("UPGRADE".equals(decision) || "DOWNGRADE".equals(decision)) {
            if (current != null) {
                memberTierMapper.closeActive(tenantId, programId, memberId, schemeId, now);
            }
            memberTierMapper.insert(UUID.randomUUID(), tenantId, programId, memberId, schemeId, target.id(), periodId, now);
            outbox.publish(EventEnvelope.builder()
                    .type("loyalty.tier.changed.v1")
                    .source("loyalty.engine-service")
                    .subject("member/" + memberId)
                    .tenantId(tenantId).programId(programId).correlationId(correlationId)
                    .data(Map.of("memberId", memberId.toString(),
                            "tierSchemeId", schemeId.toString(),
                            "previousTierId", currentTierId == null ? "" : currentTierId.toString(),
                            "newTierId", target.id().toString(),
                            "changeType", decision,
                            "transitionMode", "DIRECT",
                            "evaluationId", eval.id().toString()))
                    .build());
        }
        return eval;
    }

    private String decide(MemberTier current, List<TierView> tiers, TierView target) {
        if (current == null) return "UPGRADE";
        int currentRank = tiers.stream().filter(t -> t.id().equals(current.tierId())).map(TierView::rankNo).findFirst().orElse(0);
        if (target.rankNo() > currentRank) return "UPGRADE";
        if (target.rankNo() < currentRank) return "DOWNGRADE";
        return "MAINTAIN";
    }

    private BigDecimal threshold(TierView t) {
        if (t.config() == null || t.config().isBlank()) return BigDecimal.ZERO;
        try {
            JsonNode node = json.readTree(t.config());
            JsonNode th = node.get("threshold");
            return th == null || th.isNull() ? BigDecimal.ZERO : new BigDecimal(th.asText());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    public List<MemberTier> currentTiers(UUID tenantId, UUID programId, UUID memberId) {
        return memberTierMapper.findAllActiveByMember(tenantId, programId, memberId);
    }

    public List<MemberTier> history(UUID tenantId, UUID programId, UUID memberId, UUID schemeId) {
        return memberTierMapper.findHistory(tenantId, programId, memberId, schemeId);
    }
}

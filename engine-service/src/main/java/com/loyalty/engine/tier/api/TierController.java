package com.loyalty.engine.tier.api;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.engine.tier.TierEvaluationService;
import com.loyalty.engine.tier.api.dto.TierDtos;
import com.loyalty.engine.tier.domain.MemberTier;
import com.loyalty.engine.tier.domain.TierEvaluation;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.loyalty.engine.tier.api.dto.TierDtos.*;

/** Tier API (design 24.7 / V017 mappings). */
@RestController
@RequestMapping("/api/v1/programs/{programId}/members/{memberId}")
public class TierController {

    private final TierEvaluationService tierService;

    public TierController(TierEvaluationService tierService) {
        this.tierService = tierService;
    }

    @PostMapping("/tier-evaluations")
    public EvaluationResponse evaluate(@PathVariable UUID programId, @PathVariable UUID memberId,
                                       @RequestBody EvaluateRequest req) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        TierEvaluation e = tierService.evaluate(ctx.tenantId(), programId, memberId, req.schemeId(),
                ContextHolder.correlationId(), req.sourceEventId(), false);
        return new EvaluationResponse(e.id(), e.decision(), e.targetTierId(), e.evaluatedAt());
    }

    @PostMapping("/tier-evaluations/recalculate")
    public EvaluationResponse recalculate(@PathVariable UUID programId, @PathVariable UUID memberId,
                                          @RequestBody EvaluateRequest req) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        TierEvaluation e = tierService.evaluate(ctx.tenantId(), programId, memberId, req.schemeId(),
                ContextHolder.correlationId(), req.sourceEventId(), true);
        return new EvaluationResponse(e.id(), e.decision(), e.targetTierId(), e.evaluatedAt());
    }

    @GetMapping("/tiers")
    public List<TierResponse> tiers(@PathVariable UUID programId, @PathVariable UUID memberId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return tierService.currentTiers(ctx.tenantId(), programId, memberId).stream()
                .map(m -> new TierResponse(m.memberId(), m.tierSchemeId(), m.tierId(), m.status(), m.effectiveFrom()))
                .toList();
    }

    @GetMapping("/tiers/{schemeId}/history")
    public List<TierResponse> history(@PathVariable UUID programId, @PathVariable UUID memberId,
                                     @PathVariable UUID schemeId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return tierService.history(ctx.tenantId(), programId, memberId, schemeId).stream()
                .map(m -> new TierResponse(m.memberId(), m.tierSchemeId(), m.tierId(), m.status(), m.effectiveFrom()))
                .toList();
    }
}

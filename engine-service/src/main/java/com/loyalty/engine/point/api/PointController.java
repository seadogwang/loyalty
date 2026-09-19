package com.loyalty.engine.point.api;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.common.context.TenantContext;
import com.loyalty.engine.point.BalanceService;
import com.loyalty.engine.point.EarnService;
import com.loyalty.engine.point.RedeemService;
import com.loyalty.engine.point.RequestHasher;
import com.loyalty.engine.point.api.dto.PointDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Point API (design 24.1 / 24.5). Earn is idempotent via Idempotency-Key. */
@RestController
@RequestMapping("/api/v1/programs/{programId}/members/{memberId}/accounts/{accountId}")
public class PointController {

    private final EarnService earnService;
    private final RedeemService redeemService;
    private final BalanceService balanceService;
    private final ObjectMapper json;

    public PointController(EarnService earnService, RedeemService redeemService,
                           BalanceService balanceService, ObjectMapper json) {
        this.earnService = earnService;
        this.redeemService = redeemService;
        this.balanceService = balanceService;
        this.json = json;
    }

    @PostMapping("/point-operations/earn")
    public PointDtos.EarnResponse earn(@PathVariable UUID programId,
                                       @PathVariable UUID memberId,
                                       @PathVariable UUID accountId,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                       @RequestBody PointDtos.EarnRequest req) throws Exception {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        PrincipalContext principal = ContextHolder.principalContext();
        String requestHash = RequestHasher.hash(json.writeValueAsString(req));
        return earnService.earn(ctx.tenantId(), programId, memberId, accountId, req,
                idempotencyKey, requestHash,
                principal != null ? (principal.principalId() == null ? principal.subject() : principal.principalId().toString()) : null,
                ContextHolder.correlationId());
    }

    @PostMapping("/point-operations/redeem")
    public PointDtos.RedeemResponse redeem(@PathVariable UUID programId,
                                           @PathVariable UUID memberId,
                                           @PathVariable UUID accountId,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                           @RequestBody PointDtos.RedeemRequest req) throws Exception {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        PrincipalContext principal = ContextHolder.principalContext();
        String requestHash = RequestHasher.hash(json.writeValueAsString(req));
        return redeemService.redeem(ctx.tenantId(), programId, memberId, accountId, req,
                idempotencyKey, requestHash,
                principal != null ? (principal.principalId() == null ? principal.subject() : principal.principalId().toString()) : null,
                ContextHolder.correlationId());
    }

    @GetMapping("/balances")
    public PointDtos.BalanceResponse balances(@PathVariable UUID programId,
                                              @PathVariable UUID memberId,
                                              @PathVariable UUID accountId,
                                              @RequestParam("pointTypeId") UUID pointTypeId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        BigDecimal available = balanceService.calculateRedeemable(ctx.tenantId(), programId, accountId, pointTypeId);
        return new PointDtos.BalanceResponse(accountId,
                List.of(new PointDtos.BalanceItem(pointTypeId, available)));
    }
}

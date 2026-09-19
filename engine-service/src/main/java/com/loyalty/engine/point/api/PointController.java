package com.loyalty.engine.point.api;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.common.context.TenantContext;
import com.loyalty.engine.point.BalanceService;
import com.loyalty.engine.point.EarnService;
import com.loyalty.engine.point.PointQueryService;
import com.loyalty.engine.point.RedeemService;
import com.loyalty.engine.point.RestoreService;
import com.loyalty.engine.point.ReverseService;
import com.loyalty.engine.point.AdjustService;
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
    private final ReverseService reverseService;
    private final RestoreService restoreService;
    private final AdjustService adjustService;
    private final BalanceService balanceService;
    private final PointQueryService queryService;
    private final ObjectMapper json;

    public PointController(EarnService earnService, RedeemService redeemService,
                           ReverseService reverseService, RestoreService restoreService,
                           AdjustService adjustService, BalanceService balanceService,
                           PointQueryService queryService, ObjectMapper json) {
        this.earnService = earnService;
        this.redeemService = redeemService;
        this.reverseService = reverseService;
        this.restoreService = restoreService;
        this.adjustService = adjustService;
        this.balanceService = balanceService;
        this.queryService = queryService;
        this.json = json;
    }

    private String actor() {
        com.loyalty.common.context.PrincipalContext principal = ContextHolder.principalContext();
        return principal != null ? (principal.principalId() == null ? principal.subject() : principal.principalId().toString()) : null;
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

    @PostMapping("/point-operations/reverse")
    public PointDtos.EarnResponse reverse(@PathVariable UUID programId,
                                          @PathVariable UUID memberId,
                                          @PathVariable UUID accountId,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody PointDtos.ReverseRequest req) throws Exception {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        String requestHash = RequestHasher.hash(json.writeValueAsString(req));
        return reverseService.reverse(ctx.tenantId(), programId, memberId, accountId,
                req.referenceLedgerId(), req.amount(), req.reasonCode(),
                idempotencyKey, requestHash, actor(), ContextHolder.correlationId());
    }

    @PostMapping("/point-operations/restore")
    public PointDtos.EarnResponse restore(@PathVariable UUID programId,
                                          @PathVariable UUID memberId,
                                          @PathVariable UUID accountId,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody PointDtos.RestoreRequest req) throws Exception {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        String requestHash = RequestHasher.hash(json.writeValueAsString(req));
        return restoreService.restore(ctx.tenantId(), programId, memberId, accountId,
                req.redeemLedgerId(), req.amount(), req.reasonCode(),
                idempotencyKey, requestHash, actor(), ContextHolder.correlationId());
    }

    @PostMapping("/point-operations/adjust")
    public PointDtos.EarnResponse adjust(@PathVariable UUID programId,
                                         @PathVariable UUID memberId,
                                         @PathVariable UUID accountId,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @RequestBody PointDtos.AdjustRequest req) throws Exception {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        String requestHash = RequestHasher.hash(json.writeValueAsString(req));
        return adjustService.adjust(ctx.tenantId(), programId, memberId, accountId,
                req.pointTypeId(), req.amount(), req.reasonCode(), req.source(),
                idempotencyKey, requestHash, actor(), ContextHolder.correlationId());
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

    @GetMapping("/ledger")
    public com.loyalty.common.web.CursorPage<PointDtos.LedgerItem> ledger(
            @PathVariable UUID programId,
            @PathVariable UUID memberId,
            @PathVariable UUID accountId,
            @RequestParam("pointTypeId") UUID pointTypeId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return queryService.ledger(programId, accountId, pointTypeId, from, cursor, limit);
    }
}

package com.loyalty.engine.order.api;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.engine.order.OrderService;
import com.loyalty.engine.order.RecalculateService;
import com.loyalty.engine.order.ReturnService;
import com.loyalty.engine.order.api.dto.OrderDtos;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Order/Return/Exchange/Recalculate API (design 14 / 24.4 / V018 mappings). */
@RestController
@RequestMapping("/api/v1/programs/{programId}")
public class OrderController {

    private final OrderService orderService;
    private final ReturnService returnService;
    private final RecalculateService recalculateService;

    public OrderController(OrderService orderService, ReturnService returnService, RecalculateService recalculateService) {
        this.orderService = orderService;
        this.returnService = returnService;
        this.recalculateService = recalculateService;
    }

    @PostMapping("/orders/complete")
    public OrderDtos.OrderResponse completeOrder(@PathVariable UUID programId, @RequestBody OrderDtos.OrderRequest req) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return orderService.completeOrder(ctx.tenantId(), programId, req, ContextHolder.correlationId());
    }

    @PostMapping("/returns/complete")
    public OrderDtos.OrderResponse completeReturn(@PathVariable UUID programId, @RequestBody OrderDtos.OrderRequest req) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return returnService.completeReturn(ctx.tenantId(), programId, req, ContextHolder.correlationId());
    }

    @PostMapping("/exchanges/complete")
    public OrderDtos.OrderResponse completeExchange(@PathVariable UUID programId, @RequestBody OrderDtos.OrderRequest req) {
        // Exchange = reverse the old order's earning, then earn the new item. The request
        // carries the old orderId (reversed) plus the new order facts (earned) under one call.
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        OrderDtos.OrderResponse rev = returnService.completeReturn(ctx.tenantId(), programId, req, ContextHolder.correlationId());
        OrderDtos.OrderResponse earned = orderService.completeOrder(ctx.tenantId(), programId, req, ContextHolder.correlationId());
        return earned;
    }

    @PostMapping("/members/{memberId}/point-operations/recalculate")
    public OrderDtos.OrderResponse recalculate(@PathVariable UUID programId, @PathVariable UUID memberId,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              @RequestBody OrderDtos.OrderRequest req,
                                              @RequestParam(value = "mode", defaultValue = "CURRENT_RULE") String mode) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return recalculateService.recalculate(ctx.tenantId(), programId, req, mode, ContextHolder.correlationId());
    }
}

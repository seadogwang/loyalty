package com.loyalty.engine.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public final class OrderDtos {
    private OrderDtos() {}

    public record OrderRequest(String orderId, String channel, BigDecimal totalNetAmount, String currency,
                              UUID memberId, UUID accountId, UUID pointTypeId,
                              UUID ruleDefinitionId, UUID tierSchemeId) {}
    public record OrderResponse(String orderId, UUID operationId, UUID ledgerId, String status,
                                BigDecimal amount, String tierDecision) {}
}

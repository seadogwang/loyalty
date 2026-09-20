package com.loyalty.engine.order;

import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.engine.order.api.dto.OrderDtos;
import com.loyalty.engine.point.ReverseService;
import com.loyalty.engine.point.api.dto.PointDtos;
import com.loyalty.engine.point.domain.PointLedger;
import com.loyalty.engine.point.infrastructure.PointLedgerMapper;
import com.loyalty.engine.tier.TierEvaluationService;
import com.loyalty.engine.tier.domain.TierEvaluation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Return completion (design 14.2 / M8-T01). Locates the original EARN ledger by
 * (source_type=ORDER, source_id=orderId) and reverses it; does not modify history. Then
 * re-evaluates tier. Idempotent per return.
 */
@Service
public class ReturnService {

    private final PointLedgerMapper ledgerMapper;
    private final ReverseService reverseService;
    private final TierEvaluationService tierService;

    public ReturnService(PointLedgerMapper ledgerMapper, ReverseService reverseService,
                        TierEvaluationService tierService) {
        this.ledgerMapper = ledgerMapper;
        this.reverseService = reverseService;
        this.tierService = tierService;
    }

    @Transactional
    public OrderDtos.OrderResponse completeReturn(UUID tenantId, UUID programId, OrderDtos.OrderRequest req,
                                                  String correlationId) {
        if (req.orderId() == null || req.totalNetAmount() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "orderId and totalNetAmount required");
        }
        List<PointLedger> originals = ledgerMapper.findBySourceScoped(tenantId, programId, "ORDER", req.orderId());
        PointLedger earn = originals.stream()
                .filter(l -> l.transactionType() == com.loyalty.common.enums.TransactionType.EARN)
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "original earn not found for order"));

        String idempotencyKey = "return:" + req.orderId() + ":reverse";
        String requestHash = com.loyalty.engine.point.RequestHasher.hash(idempotencyKey + ":" + req.totalNetAmount());
        PointDtos.EarnResponse rev = reverseService.reverse(tenantId, programId, req.memberId(), req.accountId(),
                earn.id(), req.totalNetAmount(), "RETURN", idempotencyKey, requestHash, "integration", correlationId);

        String tierDecision = null;
        if (req.tierSchemeId() != null) {
            try {
                TierEvaluation eval = tierService.evaluate(tenantId, programId, req.memberId(),
                        req.tierSchemeId(), correlationId,
                        UUID.nameUUIDFromBytes(idempotencyKey.getBytes()), false);
                tierDecision = eval.decision();
            } catch (Exception ignored) { }
        }
        return new OrderDtos.OrderResponse(req.orderId(), rev.operationId(), rev.ledgerId(),
                rev.status(), rev.amount(), tierDecision);
    }
}

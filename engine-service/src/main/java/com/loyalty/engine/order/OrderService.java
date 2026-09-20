package com.loyalty.engine.order;

import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.engine.order.api.dto.OrderDtos;
import com.loyalty.engine.point.EarnService;
import com.loyalty.engine.point.RequestHasher;
import com.loyalty.engine.point.api.dto.PointDtos;
import com.loyalty.engine.point.domain.PointTypeView;
import com.loyalty.engine.point.infrastructure.PointTypeViewMapper;
import com.loyalty.engine.point.rule.RuleEngineService;
import com.loyalty.engine.point.rule.fact.OrderFact;
import com.loyalty.engine.point.rule.fact.PointTypeFact;
import com.loyalty.engine.tier.TierEvaluationService;
import com.loyalty.engine.tier.domain.TierEvaluation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order completion orchestration (design 14.1 / M8-T01). Runs the earn rule over order
 * facts, earns the computed points (idempotent per order), and optionally triggers tier
 * evaluation. The rule produces the amount; the ledger records rule id/version + snapshot.
 */
@Service
public class OrderService {

    private final RuleEngineService ruleEngine;
    private final EarnService earnService;
    private final PointTypeViewMapper pointTypeViewMapper;
    private final TierEvaluationService tierService;

    public OrderService(RuleEngineService ruleEngine, EarnService earnService,
                       PointTypeViewMapper pointTypeViewMapper, TierEvaluationService tierService) {
        this.ruleEngine = ruleEngine;
        this.earnService = earnService;
        this.pointTypeViewMapper = pointTypeViewMapper;
        this.tierService = tierService;
    }

    @Transactional
    public OrderDtos.OrderResponse completeOrder(UUID tenantId, UUID programId, OrderDtos.OrderRequest req,
                                                  String correlationId) {
        if (req.orderId() == null || req.totalNetAmount() == null || req.ruleDefinitionId() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "orderId, totalNetAmount, ruleDefinitionId required");
        }
        PointTypeView pt = pointTypeViewMapper.findScoped(tenantId, programId, req.pointTypeId());
        if (pt == null) throw new ApiException(ErrorCode.POINT_TYPE_NOT_FOUND, "point type not found");

        OrderFact order = new OrderFact(req.orderId(), req.channel() == null ? "WEB" : req.channel(),
                Instant.now(), req.totalNetAmount(), req.currency() == null ? "SGD" : req.currency());
        PointTypeFact ptFact = new PointTypeFact(req.pointTypeId(), pt != null ? "BASIC" : "BASIC",
                pt.redeemable(), pt.tierCalculable(), pt.recordOnly());
        var result = ruleEngine.execute(tenantId, programId, req.ruleDefinitionId(), correlationId,
                List.of(order, ptFact));
        if (result.getPointResults().isEmpty()) {
            throw new ApiException(ErrorCode.RULE_EVALUATION_FAILED, "earn rule produced no points");
        }
        var point = result.getPointResults().get(0);

        String idempotencyKey = "order:" + req.orderId() + ":earn";
        String requestHash = RequestHasher.hash(idempotencyKey + ":" + point.amount());
        PointDtos.EarnRequest earnReq = new PointDtos.EarnRequest(req.pointTypeId(), point.amount(),
                new PointDtos.Source("ORDER", req.orderId()), null, null);
        PointDtos.EarnResponse earn = earnService.earn(tenantId, programId, req.memberId(), req.accountId(),
                earnReq, idempotencyKey, requestHash, "integration", correlationId);

        String tierDecision = null;
        if (req.tierSchemeId() != null) {
            try {
                TierEvaluation eval = tierService.evaluate(tenantId, programId, req.memberId(),
                        req.tierSchemeId(), correlationId, UUID.nameUUIDFromBytes(idempotencyKey.getBytes()), false);
                tierDecision = eval.decision();
            } catch (Exception ignored) { /* tier eval is best-effort here */ }
        }
        return new OrderDtos.OrderResponse(req.orderId(), earn.operationId(), earn.ledgerId(),
                earn.status(), earn.amount(), tierDecision);
    }
}

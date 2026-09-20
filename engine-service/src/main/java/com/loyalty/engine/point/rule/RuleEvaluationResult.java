package com.loyalty.engine.point.rule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mutable result holder a Drools rule session writes into (design 13.6). The DRL
 * then-blocks call {@code result.addPoint(...)}; the application service reads the list
 * after {@code fireAllRules}. Drools only computes decisions — no DB, no events.
 */
public class RuleEvaluationResult {

    private String decisionType;
    private final List<PointResult> pointResults = new ArrayList<>();
    private String explanation;

    public void addPoint(UUID pointTypeId, BigDecimal amount, String reason) {
        pointResults.add(new PointResult(pointTypeId, amount, reason));
    }

    public void setDecision(String decisionType) { this.decisionType = decisionType; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getDecisionType() { return decisionType; }
    public List<PointResult> getPointResults() { return pointResults; }
    public String getExplanation() { return explanation; }
}

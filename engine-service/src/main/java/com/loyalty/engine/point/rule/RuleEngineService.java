package com.loyalty.engine.point.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.engine.point.rule.infrastructure.RuleExecutionAuditMapper;
import com.loyalty.engine.point.rule.infrastructure.RuleVersionViewMapper;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drools runtime (design 13 / M6-T02). Loads the PUBLISHED rule version's DRL, compiles a
 * KieBase (cached per version id — isolated by program/point_type/tier_scheme via the
 * version), fires the rules over caller-provided facts + a {@link RuleEvaluationResult},
 * and writes a {@code rule_execution_audit} row. Drools does decision only: no
 * Repository, no Kafka, no transactions.
 */
@Service
public class RuleEngineService {

    private static final Logger log = LoggerFactory.getLogger(RuleEngineService.class);

    private final RuleVersionViewMapper versionMapper;
    private final RuleExecutionAuditMapper auditMapper;
    private final ObjectMapper json;
    private final ConcurrentHashMap<UUID, KieContainer> cache = new ConcurrentHashMap<>();

    public RuleEngineService(RuleVersionViewMapper versionMapper, RuleExecutionAuditMapper auditMapper,
                            ObjectMapper json) {
        this.versionMapper = versionMapper;
        this.auditMapper = auditMapper;
        this.json = json;
    }

    public RuleEvaluationResult execute(UUID tenantId, UUID programId, UUID ruleDefinitionId,
                                        String correlationId, List<Object> facts) {
        RuleVersionView version = versionMapper.findPublishedByDefinition(tenantId, programId, ruleDefinitionId);
        if (version == null) {
            throw new ApiException(ErrorCode.RULE_EVALUATION_FAILED, "no PUBLISHED rule version for " + ruleDefinitionId);
        }
        String drl = extractDrl(version.metadata());
        RuleEvaluationResult result = new RuleEvaluationResult();
        long start = System.nanoTime();
        String status = "SUCCESS";
        try {
            KieContainer container = cache.computeIfAbsent(version.id(), id -> compile(id, drl));
            KieSession session = container.newKieSession();
            try {
                for (Object f : facts) session.insert(f);
                session.insert(result);
                session.fireAllRules();
            } finally {
                session.dispose();
            }
        } catch (Exception ex) {
            status = "FAILED";
            log.warn("rule execution failed: {}", ex.toString());
            throw new ApiException(ErrorCode.RULE_EVALUATION_FAILED, "rule execution failed: " + ex.getMessage());
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            audit(tenantId, programId, version, correlationId, facts, result, status, (int) elapsedMs);
        }
        return result;
    }

    private KieContainer compile(UUID versionId, String drl) {
        try {
            KieHelper helper = new KieHelper();
            helper.addContent(drl, "rule-" + versionId + ".drl");
            Results results = helper.verify();
            if (results.hasMessages(Message.Level.ERROR)) {
                throw new IllegalStateException("DRL compile error: " + results.getMessages());
            }
            return helper.getKieContainer();
        } catch (Exception ex) {
            log.warn("DRL compile failed: {}", ex.toString(), ex);
            throw new ApiException(ErrorCode.RULE_EVALUATION_FAILED,
                    "DRL compile failed: " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    private String extractDrl(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            throw new ApiException(ErrorCode.RULE_EVALUATION_FAILED, "rule version has no DRL metadata");
        }
        try {
            JsonNode node = json.readTree(metadata);
            JsonNode drl = node.get("drl");
            if (drl == null || drl.isNull()) {
                throw new ApiException(ErrorCode.RULE_EVALUATION_FAILED, "rule version metadata has no drl");
            }
            return drl.asText();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.RULE_EVALUATION_FAILED, "invalid rule version metadata");
        }
    }

    private void audit(UUID tenantId, UUID programId, RuleVersionView version, String correlationId,
                       List<Object> facts, RuleEvaluationResult result, String status, int elapsedMs) {
        try {
            auditMapper.insert(UUID.randomUUID(), tenantId, programId, version.ruleDefinitionId(),
                    version.id(), correlationId,
                    json.writeValueAsString(facts), json.writeValueAsString(result.getPointResults()),
                    status, elapsedMs);
        } catch (Exception ex) {
            log.warn("rule audit write failed: {}", ex.toString());
        }
    }
}

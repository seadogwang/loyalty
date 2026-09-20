package com.loyalty.member.rule;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.member.api.dto.RuleDtos;
import com.loyalty.member.rule.domain.RuleDefinition;
import com.loyalty.member.rule.domain.RuleVersion;
import com.loyalty.member.rule.infrastructure.RuleDefinitionMapper;
import com.loyalty.member.rule.infrastructure.RuleVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rule lifecycle (design 13.3 / M6-T01). DRAFT -> TESTING -> PUBLISHED -> RETIRED; at most
 * one PUBLISHED version per definition (enforced by the partial unique index). Rollback =
 * publish a different version (history is immutable).
 */
@Service
public class RuleService {

    private final RuleDefinitionMapper definitionMapper;
    private final RuleVersionMapper versionMapper;
    private final ObjectMapper json;

    public RuleService(RuleDefinitionMapper definitionMapper, RuleVersionMapper versionMapper, ObjectMapper json) {
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.json = json;
    }

    @Transactional
    public RuleDefinition createDefinition(UUID programId, RuleDtos.CreateRuleRequest req) {
        if (req.code() == null || req.code().isBlank() || req.domain() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "code and domain are required");
        }
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        UUID id = UUID.randomUUID();
        try {
            definitionMapper.insert(id, ctx.tenantId(), programId, req.code(), req.name(), req.domain());
        } catch (DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "rule code already exists in program");
        }
        return definitionMapper.findByIdScoped(ctx.tenantId(), programId, id);
    }

    @Transactional
    public RuleVersion createVersion(UUID programId, UUID ruleDefinitionId, RuleDtos.CreateVersionRequest req) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        RuleDefinition def = definitionMapper.findByIdScoped(ctx.tenantId(), programId, ruleDefinitionId);
        if (def == null) throw new ApiException(ErrorCode.NOT_FOUND, "rule definition not found");
        Integer maxVersion = versionMapper.findByDefinition(ctx.tenantId(), programId, ruleDefinitionId).stream()
                .map(RuleVersion::versionNo).max(Integer::compareTo).orElse(0);
        UUID id = UUID.randomUUID();
        String metadata;
        try {
            metadata = json.writeValueAsString(Map.of("drl", req.drl() == null ? "" : req.drl()));
        } catch (Exception e) { metadata = "{}"; }
        versionMapper.insert(id, ctx.tenantId(), programId, ruleDefinitionId, maxVersion + 1, "DRAFT",
                metadata, req.createdBy());
        return versionMapper.findByIdScoped(ctx.tenantId(), programId, id);
    }

    @Transactional
    public RuleVersion publish(UUID programId, UUID versionId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        RuleVersion v = versionMapper.findByIdScoped(ctx.tenantId(), programId, versionId);
        if (v == null) throw new ApiException(ErrorCode.NOT_FOUND, "rule version not found");
        if (!v.canTransitionTo("PUBLISHED")) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "cannot publish from status " + v.status());
        }
        try {
            versionMapper.updateStatus(versionId, "PUBLISHED", java.time.Clock.systemUTC().instant(), null);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "a PUBLISHED version already exists for this rule");
        }
        return versionMapper.findByIdScoped(ctx.tenantId(), programId, versionId);
    }

    @Transactional
    public RuleVersion retire(UUID programId, UUID versionId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        RuleVersion v = versionMapper.findByIdScoped(ctx.tenantId(), programId, versionId);
        if (v == null) throw new ApiException(ErrorCode.NOT_FOUND, "rule version not found");
        if (!v.canTransitionTo("RETIRED")) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "cannot retire from status " + v.status());
        }
        versionMapper.updateStatus(versionId, "RETIRED", null, java.time.Clock.systemUTC().instant());
        return versionMapper.findByIdScoped(ctx.tenantId(), programId, versionId);
    }

    public RuleVersion getPublished(UUID programId, UUID ruleDefinitionId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return versionMapper.findPublishedByDefinition(ctx.tenantId(), programId, ruleDefinitionId);
    }

    public List<RuleDefinition> listDefinitions(UUID programId) {
        TenantContext ctx = ContextHolder.tenantContext().requireTenant();
        return definitionMapper.listByProgram(ctx.tenantId(), programId);
    }
}

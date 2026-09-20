package com.loyalty.member.api;

import com.loyalty.member.api.dto.RuleDtos;
import com.loyalty.member.rule.RuleService;
import com.loyalty.member.rule.domain.RuleDefinition;
import com.loyalty.member.rule.domain.RuleVersion;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.loyalty.member.api.dto.RuleDtos.*;

/** Rule lifecycle API (design 13.3 / M6-T01 / V016 mappings). */
@RestController
@RequestMapping("/api/v1/programs/{programId}/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public RuleDefinitionResponse create(@PathVariable UUID programId, @RequestBody CreateRuleRequest req) {
        RuleDefinition d = ruleService.createDefinition(programId, req);
        return new RuleDefinitionResponse(d.id(), d.code(), d.name(), d.domain(), d.status());
    }

    @GetMapping
    public List<RuleDefinitionResponse> list(@PathVariable UUID programId) {
        return ruleService.listDefinitions(programId).stream()
                .map(d -> new RuleDefinitionResponse(d.id(), d.code(), d.name(), d.domain(), d.status()))
                .toList();
    }

    @PostMapping("/{ruleDefinitionId}/versions")
    public RuleVersionResponse createVersion(@PathVariable UUID programId,
                                             @PathVariable UUID ruleDefinitionId,
                                             @RequestBody CreateVersionRequest req) {
        RuleVersion v = ruleService.createVersion(programId, ruleDefinitionId, req);
        return new RuleVersionResponse(v.id(), v.ruleDefinitionId(), v.versionNo(), v.status(), v.metadata());
    }

    @PostMapping("/versions/{versionId}/publish")
    public RuleVersionResponse publish(@PathVariable UUID programId, @PathVariable UUID versionId) {
        RuleVersion v = ruleService.publish(programId, versionId);
        return new RuleVersionResponse(v.id(), v.ruleDefinitionId(), v.versionNo(), v.status(), v.metadata());
    }

    @PostMapping("/versions/{versionId}/retire")
    public RuleVersionResponse retire(@PathVariable UUID programId, @PathVariable UUID versionId) {
        RuleVersion v = ruleService.retire(programId, versionId);
        return new RuleVersionResponse(v.id(), v.ruleDefinitionId(), v.versionNo(), v.status(), v.metadata());
    }

    @GetMapping("/{ruleDefinitionId}/published")
    public RuleVersionResponse published(@PathVariable UUID programId, @PathVariable UUID ruleDefinitionId) {
        RuleVersion v = ruleService.getPublished(programId, ruleDefinitionId);
        if (v == null) throw new com.loyalty.common.error.ApiException(com.loyalty.common.error.ErrorCode.NOT_FOUND, "no published version");
        return new RuleVersionResponse(v.id(), v.ruleDefinitionId(), v.versionNo(), v.status(), v.metadata());
    }
}

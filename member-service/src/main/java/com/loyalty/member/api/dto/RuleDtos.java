package com.loyalty.member.api.dto;

import java.util.UUID;

public final class RuleDtos {
    private RuleDtos() {}

    public record CreateRuleRequest(String code, String name, String domain) {}
    public record CreateVersionRequest(String drl, String createdBy) {}
    public record RuleDefinitionResponse(UUID id, String code, String name, String domain, String status) {}
    public record RuleVersionResponse(UUID id, UUID ruleDefinitionId, Integer versionNo,
                                       String status, String metadata) {}
}

package com.loyalty.engine.point.rule;

import java.util.UUID;

/** Read-only view of a published rule version (engine reads rule_version from the shared DB). */
public record RuleVersionView(UUID id, UUID ruleDefinitionId, Integer versionNo, String metadata) {
}

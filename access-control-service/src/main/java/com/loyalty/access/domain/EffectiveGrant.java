package com.loyalty.access.domain;

import java.util.UUID;

/**
 * A permission granted to a principal through a binding, annotated with the binding's
 * scope so the decision layer can check scope sufficiency and tenant/program context.
 */
public record EffectiveGrant(String permissionCode, ScopeType scopeType, UUID tenantId, UUID programId) {
}

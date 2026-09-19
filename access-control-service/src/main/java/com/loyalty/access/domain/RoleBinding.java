package com.loyalty.access.domain;

import java.util.UUID;

/**
 * A principal's active role binding within a scope (design 27.1 / auth_principal_role).
 */
public record RoleBinding(
        UUID bindingId,
        UUID principalId,
        UUID roleId,
        String roleCode,
        ScopeType scopeType,
        UUID tenantId,
        UUID programId) {
}

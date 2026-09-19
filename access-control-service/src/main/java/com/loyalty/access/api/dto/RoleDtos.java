package com.loyalty.access.api.dto;

import java.time.Instant;
import java.util.UUID;

public final class RoleDtos {
    private RoleDtos() {}

    public record CreateRoleRequest(String code, String name, String scopeType) {}
    public record UpdateRoleRequest(String name) {}
    public record RoleResponse(UUID id, String code, String name, String scopeKey,
                                String scopeType, UUID tenantId, UUID programId,
                                boolean managed, String status) {}

    public record GrantBindingRequest(UUID roleId, String scopeType,
                                      Instant effectiveFrom, Instant effectiveTo, String reason) {}
    public record BindingResponse(UUID bindingId, UUID principalId, UUID roleId, String scopeType,
                                   UUID tenantId, UUID programId, String status) {}

    public record CreateApprovalRequest(String requestType, String requestId, String requiredPermission,
                                        String reason) {}
    public record ApprovalResponse(UUID approvalId, String requestType, String requestId,
                                   String status, String requiredPermission) {}

    public record EffectivePermissionResponse(UUID principalId, java.util.List<String> permissions) {}
}

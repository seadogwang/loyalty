package com.loyalty.access.api;

import com.loyalty.access.api.dto.RoleDtos;
import com.loyalty.access.repository.AccessCommandRepository;
import com.loyalty.common.access.audit.AuditService;
import com.loyalty.common.access.authorization.EffectiveGrantCache;
import com.loyalty.common.access.domain.ScopeType;
import com.loyalty.common.access.repository.AccessReadMapper;
import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.loyalty.access.api.dto.RoleDtos.*;

/**
 * Role binding API (design 27.4). Grant/revoke are idempotent; revoke invalidates the
 * grant cache so subsequent requests fail immediately. Tenant/program scope is derived
 * from the caller's trusted {@link TenantContext}, never from the request body.
 */
@RestController
@RequestMapping("/api/v1/admin/principals/{principalId}")
public class RoleBindingController {

    private final AccessCommandRepository command;
    private final AccessReadMapper read;
    private final EffectiveGrantCache cache;
    private final AuditService audit;

    public RoleBindingController(AccessCommandRepository command, AccessReadMapper read,
                                 EffectiveGrantCache cache, AuditService audit) {
        this.command = command;
        this.read = read;
        this.cache = cache;
        this.audit = audit;
    }

    @PostMapping("/role-bindings")
    @Transactional
    public BindingResponse grant(@PathVariable UUID principalId, @RequestBody GrantBindingRequest req) {
        if (req.roleId() == null || req.scopeType() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "roleId and scopeType are required");
        }
        ScopeType scope = ScopeType.valueOf(req.scopeType());
        TenantContext ctx = ContextHolder.tenantContext();
        UUID tenantId = switch (scope) {
            case TENANT, PROGRAM -> ctx.requireTenantId();
            default -> null;
        };
        UUID programId = switch (scope) {
            case PROGRAM -> ctx.requireProgramId();
            default -> null;
        };

        UUID actorId = currentPrincipalId();
        UUID bindingId = command.grantRoleBinding(principalId, req.roleId(), scope,
                tenantId, programId, req.effectiveFrom(), req.effectiveTo(), actorId == null ? null : actorId.toString());
        cache.invalidate(principalId);

        audit.recordRole("ROLE_GRANTED", actorId, bindingId.toString(), "role.binding.write",
                tenantId, programId, ContextHolder.correlationId(),
                java.util.Map.of("principalId", principalId, "roleId", req.roleId(),
                        "scope", scope.name(), "reason", req.reason() == null ? "" : req.reason()));
        return new BindingResponse(bindingId, principalId, req.roleId(), scope.name(),
                tenantId, programId, "ACTIVE");
    }

    @DeleteMapping("/role-bindings/{bindingId}")
    @Transactional
    public void revoke(@PathVariable UUID principalId, @PathVariable UUID bindingId) {
        boolean revoked = command.revokeRoleBinding(bindingId, Instant.now());
        cache.invalidate(principalId);
        UUID actorId = currentPrincipalId();
        audit.recordRole("ROLE_REVOKED", actorId, bindingId.toString(), "role.binding.write",
                null, null, ContextHolder.correlationId(),
                java.util.Map.of("principalId", principalId, "applied", revoked));
    }

    @GetMapping("/effective-permissions")
    public EffectivePermissionResponse effectivePermissions(@PathVariable UUID principalId) {
        List<String> codes = read.findEffectiveGrants(principalId).stream()
                .map(g -> g.permissionCode())
                .distinct()
                .toList();
        return new EffectivePermissionResponse(principalId, codes);
    }

    private UUID currentPrincipalId() {
        PrincipalContext p = ContextHolder.principalContext();
        return p == null ? null : p.principalId();
    }
}

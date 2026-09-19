package com.loyalty.access.api;

import com.loyalty.access.api.dto.RoleDtos;
import com.loyalty.access.audit.AuditService;
import com.loyalty.access.domain.ScopeType;
import com.loyalty.access.repository.AccessCommandRepository;
import com.loyalty.access.repository.AccessReadRepository;
import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.loyalty.access.api.dto.RoleDtos.*;

/**
 * Role management API (design 27.4). Managed roles are immutable through this API;
 * only custom (non-managed) roles may be created, updated, or deactivated.
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleController {

    private final AccessCommandRepository command;
    private final AccessReadRepository read;
    private final AuditService audit;
    private final JdbcClient jdbc;

    public RoleController(AccessCommandRepository command, AccessReadRepository read,
                          AuditService audit, JdbcClient jdbc) {
        this.command = command;
        this.read = read;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return jdbc.sql("""
                SELECT id, code, name, scope_key, tenant_id, program_id, managed, status
                FROM loyalty.auth_role
                WHERE status = 'ACTIVE'
                ORDER BY scope_key, code
                """)
                .query((rs, n) -> new RoleResponse(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("scope_key"), scopeType(rs.getString("scope_key")),
                        rs.getObject("tenant_id", UUID.class), rs.getObject("program_id", UUID.class),
                        rs.getBoolean("managed"), rs.getString("status")))
                .list();
    }

    @GetMapping("/{roleId}")
    public RoleResponse get(@PathVariable UUID roleId) {
        return jdbc.sql("""
                SELECT id, code, name, scope_key, tenant_id, program_id, managed, status
                FROM loyalty.auth_role WHERE id = :id
                """)
                .param("id", roleId)
                .query((rs, n) -> new RoleResponse(
                        rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("scope_key"), scopeType(rs.getString("scope_key")),
                        rs.getObject("tenant_id", UUID.class), rs.getObject("program_id", UUID.class),
                        rs.getBoolean("managed"), rs.getString("status")))
                .optional().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "role not found"));
    }

    @PostMapping
    public RoleResponse create(@RequestBody CreateRoleRequest req) {
        if (req.code() == null || req.code().isBlank() || req.name() == null || req.name().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "code and name are required");
        }
        ScopeType scope = ScopeType.valueOf(req.scopeType());
        TenantContext ctx = ContextHolder.tenantContext();
        UUID tenantId = scope == ScopeType.TENANT || scope == ScopeType.PROGRAM ? ctx.requireTenantId() : null;
        UUID programId = scope == ScopeType.PROGRAM ? ctx.requireProgramId() : null;
        UUID id = command.createRole(req.code(), req.name(), scope, tenantId, programId);
        PrincipalContext principal = ContextHolder.principalContext();
        audit.recordRole("ROLE_CREATED", principal != null ? principal.principalId() : null,
                id.toString(), null, tenantId, programId, ContextHolder.correlationId(),
                java.util.Map.of("code", req.code(), "scope", scope.name()));
        return get(id);
    }

    @PatchMapping("/{roleId}")
    public RoleResponse update(@PathVariable UUID roleId, @RequestBody UpdateRoleRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "name is required");
        }
        if (!command.updateRole(roleId, req.name())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "role not found or is managed (immutable)");
        }
        PrincipalContext principal = ContextHolder.principalContext();
        audit.recordRole("ROLE_UPDATED", principal != null ? principal.principalId() : null,
                roleId.toString(), null, null, null, ContextHolder.correlationId(),
                java.util.Map.of("name", req.name()));
        return get(roleId);
    }

    @PostMapping("/{roleId}/deactivate")
    public RoleResponse deactivate(@PathVariable UUID roleId) {
        if (!command.deactivateRole(roleId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "role not found, managed, or already inactive");
        }
        PrincipalContext principal = ContextHolder.principalContext();
        audit.recordRole("ROLE_UPDATED", principal != null ? principal.principalId() : null,
                roleId.toString(), null, null, null, ContextHolder.correlationId(),
                java.util.Map.of("action", "deactivate"));
        return get(roleId);
    }

    private static String scopeType(String scopeKey) {
        if (scopeKey == null || scopeKey.equals("SYSTEM")) return "SYSTEM";
        if (scopeKey.startsWith("TENANT:")) return "TENANT";
        if (scopeKey.startsWith("PROGRAM:")) return "PROGRAM";
        return "SYSTEM";
    }
}

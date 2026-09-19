package com.loyalty.access.api;

import com.loyalty.access.api.dto.RoleDtos;
import com.loyalty.access.api.dto.RoleDtos.ApprovalResponse;
import com.loyalty.access.audit.AuditService;
import com.loyalty.access.repository.AccessCommandRepository;
import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.error.ApiException;
import com.loyalty.common.error.ErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Authorization approval API (design 27.4). High-risk operations reference an approved
 * {@code approvalId} before executing. The approver must differ from the requester
 * (DB CHECK + explicit application check returning a clear error).
 */
@RestController
@RequestMapping("/api/v1/admin/authorization-approvals")
public class ApprovalController {

    private final AccessCommandRepository command;
    private final AuditService audit;
    private final JdbcClient jdbc;

    public ApprovalController(AccessCommandRepository command, AuditService audit, JdbcClient jdbc) {
        this.command = command;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @PostMapping
    @Transactional
    public ApprovalResponse create(@RequestBody RoleDtos.CreateApprovalRequest req) {
        if (req.requestType() == null || req.requestId() == null || req.requiredPermission() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "requestType, requestId, requiredPermission required");
        }
        PrincipalContext principal = ContextHolder.principalContext();
        if (principal == null || principal.principalId() == null) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "principal must be registered");
        }
        TenantContext ctx = ContextHolder.tenantContext();
        UUID tenantId = ctx == null ? null : ctx.tenantId();
        UUID programId = ctx == null ? null : ctx.programId();
        UUID id = command.createApproval(req.requestType(), req.requestId(), principal.principalId(),
                req.requiredPermission(), tenantId, programId, req.reason());
        audit.recordAuthz("APPROVAL_REQUESTED", principal.principalId(), req.requiredPermission(),
                "/api/v1/admin/authorization-approvals", "POST", tenantId, programId,
                null, ContextHolder.correlationId(),
                java.util.Map.of("approvalId", id, "requestType", req.requestType()));
        return new ApprovalResponse(id, req.requestType(), req.requestId(), "REQUESTED", req.requiredPermission());
    }

    @PostMapping("/{approvalId}/approve")
    @Transactional
    public ApprovalResponse approve(@PathVariable UUID approvalId) {
        return decide(approvalId, true);
    }

    @PostMapping("/{approvalId}/reject")
    @Transactional
    public ApprovalResponse reject(@PathVariable UUID approvalId) {
        return decide(approvalId, false);
    }

    private ApprovalResponse decide(UUID approvalId, boolean approved) {
        UUID requester = jdbc.sql("""
                SELECT requester_principal_id FROM loyalty.authorization_approval WHERE id = :id
                """)
                .param("id", approvalId).query(UUID.class).optional().orElse(null);
        if (requester == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "approval not found");
        }
        PrincipalContext principal = ContextHolder.principalContext();
        UUID approver = principal == null ? null : principal.principalId();
        if (approver != null && approver.equals(requester)) {
            throw new ApiException(ErrorCode.ROLE_SCOPE_VIOLATION, "requester cannot approve their own request");
        }
        boolean ok = command.decideApproval(approvalId, approver, approved);
        if (!ok) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "approval not in REQUESTED state");
        }
        audit.recordAuthz(approved ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED", approver, null,
                "/api/v1/admin/authorization-approvals/" + approvalId, "POST",
                null, null, null, ContextHolder.correlationId(),
                java.util.Map.of("approvalId", approvalId, "approved", approved));
        return new ApprovalResponse(approvalId, null, null, approved ? "APPROVED" : "REJECTED", null);
    }
}

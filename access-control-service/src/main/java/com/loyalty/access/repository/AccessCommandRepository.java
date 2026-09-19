package com.loyalty.access.repository;

import com.loyalty.common.access.domain.ScopeType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Write-side of the authorization tables (design 27.4). Grant/revoke are idempotent:
 * the unique-active index on {@code auth_principal_role} absorbs duplicate grants, and
 * revoke is a no-op if the binding is already revoked. Every mutation is paired with an
 * {@code authorization_audit} row (written by the caller via {@code AuditService}) and,
 * where applicable, an outbox event.
 */
@Repository
public class AccessCommandRepository {

    private final JdbcClient jdbc;

    public AccessCommandRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grant a role binding. Returns the binding id (existing or newly created). Idempotent:
     * a duplicate active grant for the same (principal, role, scope) is a no-op.
     */
    public UUID grantRoleBinding(UUID principalId, UUID roleId, ScopeType scopeType,
                                 UUID tenantId, UUID programId, Instant effectiveFrom,
                                 Instant effectiveTo, String actorId) {
        UUID bindingId = UUID.randomUUID();
        int rows = jdbc.sql("""
                INSERT INTO loyalty.auth_principal_role
                  (id, principal_id, role_id, scope_type, tenant_id, program_id,
                   effective_from, effective_to, status, created_by)
                VALUES (:id, :principal, :role, :scope, :tenant, :program,
                        :from, :to, 'ACTIVE', :actor)
                ON CONFLICT DO NOTHING
                """)
                .param("id", bindingId)
                .param("principal", principalId)
                .param("role", roleId)
                .param("scope", scopeType.name())
                .param("tenant", tenantId)
                .param("program", programId)
                .param("from", effectiveFrom == null ? Instant.now() : effectiveFrom)
                .param("to", effectiveTo)
                .param("actor", actorId)
                .update();
        if (rows == 0) {
            // Already an active binding — return its id.
            return jdbc.sql("""
                    SELECT id FROM loyalty.auth_principal_role
                    WHERE principal_id = :principal AND role_id = :role AND scope_type = :scope
                      AND status = 'ACTIVE' AND effective_to IS NULL
                    """)
                    .param("principal", principalId)
                    .param("role", roleId)
                    .param("scope", scopeType.name())
                    .query(UUID.class)
                    .optional().orElse(bindingId);
        }
        return bindingId;
    }

    /** Revoke an active binding. Returns true if a binding was revoked. */
    public boolean revokeRoleBinding(UUID bindingId, Instant now) {
        int rows = jdbc.sql("""
                UPDATE loyalty.auth_principal_role
                SET status = 'REVOKED', effective_to = :now
                WHERE id = :id AND status = 'ACTIVE'
                """)
                .param("id", bindingId)
                .param("now", now == null ? Instant.now() : now)
                .update();
        return rows > 0;
    }

    /** Create a custom (non-managed) role. Managed roles are seed-only. */
    public UUID createRole(String code, String name, ScopeType scopeType, UUID tenantId, UUID programId) {
        String scopeKey = switch (scopeType) {
            case SYSTEM -> "SYSTEM";
            case TENANT -> "TENANT:" + (tenantId == null ? "" : tenantId);
            case PROGRAM -> "PROGRAM:" + (programId == null ? "" : programId);
        };
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO loyalty.auth_role (id, code, name, scope_key, tenant_id, program_id, managed, status)
                VALUES (:id, :code, :name, :scopeKey, :tenant, :program, false, 'ACTIVE')
                """)
                .param("id", id)
                .param("code", code)
                .param("name", name)
                .param("scopeKey", scopeKey)
                .param("tenant", tenantId)
                .param("program", programId)
                .update();
        return id;
    }

    /** Deactivate a role (only non-managed roles; managed roles are immutable). */
    public boolean deactivateRole(UUID roleId) {
        return jdbc.sql("""
                UPDATE loyalty.auth_role SET status = 'INACTIVE'
                WHERE id = :id AND managed = false AND status = 'ACTIVE'
                """)
                .param("id", roleId)
                .update() > 0;
    }

    /** Update a non-managed role's name. */
    public boolean updateRole(UUID roleId, String name) {
        return jdbc.sql("""
                UPDATE loyalty.auth_role SET name = :name, updated_at = now()
                WHERE id = :id AND managed = false
                """)
                .param("id", roleId)
                .param("name", name)
                .update() > 0;
    }

    public UUID createApproval(String requestType, String requestId, UUID requesterPrincipalId,
                              String requiredPermission, UUID tenantId, UUID programId, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO loyalty.authorization_approval
                  (id, request_type, request_id, requester_principal_id, required_permission,
                   tenant_id, program_id, status, reason)
                VALUES (:id, :rt, :rid, :requester, :perm, :tenant, :program, 'REQUESTED', :reason)
                """)
                .param("id", id)
                .param("rt", requestType)
                .param("rid", requestId)
                .param("requester", requesterPrincipalId)
                .param("perm", requiredPermission)
                .param("tenant", tenantId)
                .param("program", programId)
                .param("reason", reason)
                .update();
        return id;
    }

    /** Approve an approval request. The approver must differ from the requester (DB CHECK). */
    public boolean decideApproval(UUID approvalId, UUID approverPrincipalId, boolean approved) {
        String status = approved ? "APPROVED" : "REJECTED";
        return jdbc.sql("""
                UPDATE loyalty.authorization_approval
                SET status = :status, approver_principal_id = :approver, decided_at = now()
                WHERE id = :id AND status = 'REQUESTED'
                """)
                .param("status", status)
                .param("approver", approverPrincipalId)
                .param("id", approvalId)
                .update() > 0;
    }
}

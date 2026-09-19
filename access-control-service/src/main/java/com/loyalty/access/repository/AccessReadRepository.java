package com.loyalty.access.repository;

import com.loyalty.access.domain.EffectiveGrant;
import com.loyalty.access.domain.RoleBinding;
import com.loyalty.access.domain.ScopeType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Read-side access to the authorization tables. All queries are scope-aware: a binding
 * is only effective if its scope is satisfied by the request's tenant/program context.
 *
 * <p>Writes (grant/revoke) live in {@code AccessCommandRepository}; this repository is
 * read-only to keep the enforcement path free of accidental mutations.
 */
@Repository
public class AccessReadRepository {

    private final JdbcClient jdbc;

    public AccessReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Load a principal's id by (principal_type, subject). Returns null if unknown. */
    public UUID findPrincipalId(String principalType, String subject) {
        return jdbc.sql("""
                SELECT id FROM loyalty.auth_principal
                WHERE principal_type = :type AND subject = :subject AND status = 'ACTIVE'
                """)
                .param("type", principalType)
                .param("subject", subject)
                .query(UUID.class)
                .optional().orElse(null);
    }

    /**
     * Effective grants for a principal: each active binding's permission codes annotated
     * with the binding's scope + tenant/program. Used by the decision layer to check
     * scope sufficiency and context match. Not filtered by request scope here — the
     * decision layer applies that, so a single cache entry serves all scopes.
     */
    public List<EffectiveGrant> findEffectiveGrants(UUID principalId) {
        return jdbc.sql("""
                SELECT DISTINCT p.code, pr.scope_type, pr.tenant_id, pr.program_id
                FROM loyalty.auth_principal_role pr
                JOIN loyalty.auth_role_permission rp ON rp.role_id = pr.role_id
                JOIN loyalty.auth_permission p ON p.id = rp.permission_id
                WHERE pr.principal_id = :principalId
                  AND pr.status = 'ACTIVE'
                  AND (pr.effective_to IS NULL OR pr.effective_to > now())
                """)
                .param("principalId", principalId)
                .query((rs, n) -> new EffectiveGrant(
                        rs.getString("code"),
                        ScopeType.valueOf(rs.getString("scope_type")),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("program_id", UUID.class)))
                .list();
    }

    /** Active role bindings for a principal (for effective-permissions / audit views). */
    public List<RoleBinding> findActiveBindings(UUID principalId) {
        return jdbc.sql("""
                SELECT pr.id, pr.principal_id, pr.role_id, r.code, pr.scope_type,
                       pr.tenant_id, pr.program_id
                FROM loyalty.auth_principal_role pr
                JOIN loyalty.auth_role r ON r.id = pr.role_id
                WHERE pr.principal_id = :principalId
                  AND pr.status = 'ACTIVE'
                  AND (pr.effective_to IS NULL OR pr.effective_to > now())
                """)
                .param("principalId", principalId)
                .query((rs, n) -> new RoleBinding(
                        rs.getObject("id", UUID.class),
                        rs.getObject("principal_id", UUID.class),
                        rs.getObject("role_id", UUID.class),
                        rs.getString("code"),
                        ScopeType.valueOf(rs.getString("scope_type")),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getObject("program_id", UUID.class)))
                .list();
    }
}

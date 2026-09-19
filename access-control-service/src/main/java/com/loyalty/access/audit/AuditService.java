package com.loyalty.access.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Writes {@code authorization_audit} rows for authorization decisions and role mutations
 * (design 27). Failures here must never block the request path; audit is best-effort and
 * logged if the write fails.
 */
@Service
public class AuditService {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public AuditService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void recordAuthz(String action, UUID actorPrincipalId, String permissionCode,
                            String requestPath, String method, UUID tenantId, UUID programId,
                            String requestId, String correlationId, Map<String, Object> detail) {
        record(action, actorPrincipalId, "AUTHZ", permissionCode, tenantId, programId,
                requestId, correlationId, method + " " + requestPath, detail);
    }

    public void recordRole(String action, UUID actorPrincipalId, String targetId,
                           String permissionCode, UUID tenantId, UUID programId,
                           String correlationId, Map<String, Object> detail) {
        record(action, actorPrincipalId, "ROLE", targetId, tenantId, programId,
                null, correlationId, permissionCode, detail);
    }

    private void record(String action, UUID actorPrincipalId, String targetType, String targetId,
                        UUID tenantId, UUID programId, String requestId, String correlationId,
                        String permissionCode, Map<String, Object> detail) {
        try {
            jdbc.sql("""
                    INSERT INTO loyalty.authorization_audit
                      (actor_principal_id, action, target_type, target_id, permission_code,
                       tenant_id, program_id, request_id, correlation_id, detail_json)
                    VALUES (:actor, :action, :tgtType, :tgtId, :perm,
                            :tenant, :program, :req, :corr, :detail::jsonb)
                    """)
                    .param("actor", actorPrincipalId)
                    .param("action", action)
                    .param("tgtType", targetType)
                    .param("tgtId", targetId)
                    .param("perm", permissionCode)
                    .param("tenant", tenantId)
                    .param("program", programId)
                    .param("req", requestId)
                    .param("corr", correlationId)
                    .param("detail", json.writeValueAsString(detail == null ? Map.of() : detail))
                    .update();
        } catch (Exception ex) {
            // Audit must never break the request; the authorization filter still enforces.
            org.slf4j.LoggerFactory.getLogger(AuditService.class)
                    .warn("authorization_audit write failed: action={} reason={}", action, ex.toString());
        }
    }
}

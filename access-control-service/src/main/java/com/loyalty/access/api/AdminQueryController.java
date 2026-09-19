package com.loyalty.access.api;

import com.loyalty.common.web.CursorPage;
import com.loyalty.common.web.CursorRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only query endpoints (design 27.4): list permissions and query the authorization
 * audit trail. The enforcement filter gates both via {@code audit.read} /
 * {@code role.binding.write} API mappings.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminQueryController {

    private final JdbcClient jdbc;

    public AdminQueryController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/permissions")
    public List<Map<String, Object>> permissions() {
        return jdbc.sql("""
                SELECT code, resource, action, description, managed
                FROM loyalty.auth_permission ORDER BY code
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("code", rs.getString("code"));
                    m.put("resource", rs.getString("resource"));
                    m.put("action", rs.getString("action"));
                    m.put("managed", rs.getBoolean("managed"));
                    return m;
                })
                .list();
    }

    @GetMapping("/authorization-audit")
    public CursorPage<Map<String, Object>> audit(@RequestParam(required = false) String cursor,
                                                  @RequestParam(required = false) Integer limit,
                                                  @RequestParam(required = false) UUID tenantId,
                                                  @RequestParam(required = false) UUID programId) {
        CursorRequest page = CursorRequest.of(cursor, limit);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        if (tenantId != null) where.append(" AND tenant_id = :tenant ");
        if (programId != null) where.append(" AND program_id = :program ");
        // Newest first; cursor is the id of the last seen row (simplest opaque cursor).
        String sql = "SELECT id, actor_principal_id, action, target_type, target_id, permission_code, " +
                "tenant_id, program_id, correlation_id, created_at " +
                "FROM loyalty.authorization_audit " + where +
                "ORDER BY created_at DESC, id DESC LIMIT :limit";
        var q = jdbc.sql(sql).param("limit", page.limit() + 1);
        if (tenantId != null) q.param("tenant", tenantId);
        if (programId != null) q.param("program", programId);
        List<Map<String, Object>> items = q.query(mapRow()).list();
        String nextCursor = null;
        if (items.size() > page.limit()) {
            items = items.subList(0, page.limit());
            nextCursor = items.get(items.size() - 1).get("id").toString();
        }
        return CursorPage.of(items, nextCursor);
    }

    private static org.springframework.jdbc.core.RowMapper<Map<String, Object>> mapRow() {
        return (rs, n) -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", rs.getObject("id", UUID.class));
            m.put("actorPrincipalId", rs.getObject("actor_principal_id", UUID.class));
            m.put("action", rs.getString("action"));
            m.put("targetType", rs.getString("target_type"));
            m.put("targetId", rs.getString("target_id"));
            m.put("permissionCode", rs.getString("permission_code"));
            m.put("tenantId", rs.getObject("tenant_id", UUID.class));
            m.put("programId", rs.getObject("program_id", UUID.class));
            m.put("correlationId", rs.getString("correlation_id"));
            m.put("createdAt", rs.getString("created_at"));
            return m;
        };
    }
}

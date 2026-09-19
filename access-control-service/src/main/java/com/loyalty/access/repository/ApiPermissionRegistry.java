package com.loyalty.access.repository;

import com.loyalty.access.domain.ApiPermission;
import com.loyalty.access.domain.ScopeType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Loads the {@code auth_api_permission} matrix at startup and resolves an inbound
 * (method, path) to its required permission + scope. Unmapped APIs resolve to null,
 * which the enforcement layer treats as <strong>deny by default</strong> (design 27.3).
 *
 * <p>Path templates use Spring-style {@code {name}} placeholders; a concrete path
 * matches when each placeholder absorbs a single path segment.
 */
@Component
public class ApiPermissionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ApiPermissionRegistry.class);

    private record Entry(Pattern pattern, String method, ApiPermission permission) {}
    private final List<Entry> entries = new ArrayList<>();

    private final JdbcClient jdbc;

    public ApiPermissionRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void refresh() {
        entries.clear();
        jdbc.sql("""
                SELECT http_method, path_template, p.code, a.scope_type
                FROM loyalty.auth_api_permission a
                JOIN loyalty.auth_permission p ON p.id = a.permission_id
                WHERE a.status = 'ACTIVE'
                """)
                .query((rs, n) -> {
                    String method = rs.getString("http_method");
                    String template = rs.getString("path_template");
                    String code = rs.getString("code");
                    ScopeType scope = ScopeType.valueOf(rs.getString("scope_type"));
                    return new Entry(toPattern(template), method,
                            new ApiPermission(method, template, code, scope));
                })
                .list()
                .forEach(entries::add);
        log.info("loaded {} api permission mappings", entries.size());
    }

    /** Resolve a concrete request to its required permission, or null if unmapped. */
    public ApiPermission resolve(String method, String path) {
        for (Entry e : entries) {
            if (e.method().equalsIgnoreCase(method) && e.pattern().matcher(path).matches()) {
                return e.permission();
            }
        }
        return null;
    }

    /**
     * Convert a Spring-style path template to a regex. {@code {name}} becomes
     * {@code [^/]+}; all other characters are quoted literally.
     */
    static Pattern toPattern(String template) {
        StringBuilder out = new StringBuilder("^");
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i);
                if (end < 0) {
                    out.append(Pattern.quote(template.substring(i)));
                    break;
                }
                out.append("[^/]+");
                i = end + 1;
            } else {
                out.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        out.append("$");
        return Pattern.compile(out.toString());
    }
}

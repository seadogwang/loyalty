package com.loyalty.access.security;

import com.loyalty.access.repository.AccessReadRepository;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.common.context.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Derives the trusted {@link PrincipalContext} and {@link TenantContext} from a verified
 * JWT (design 27.2). Tenant/program are NEVER taken from request bodies or path params;
 * they come only from the verified token claims.
 *
 * <p>Claim conventions (configurable):
 * <ul>
 *   <li>{@code sub} / {@code preferred_username} -> principal subject</li>
 *   <li>{@code principal_type} -> USER | SERVICE_ACCOUNT | API_CLIENT (default USER)</li>
 *   <li>{@code tenant_id} / {@code program_id} -> scope claims (optional)</li>
 * </ul>
 *
 * <p>If a principal subject is unknown to the system, a zero-permission PrincipalContext is
 * still produced (design M2-T01: first-seen valid subject may exist with no permissions);
 * the enforcement layer then denies everything by default.
 */
@Component
public class JwtContextResolver {

    private final AccessReadRepository accessReadRepository;

    public JwtContextResolver(AccessReadRepository accessReadRepository) {
        this.accessReadRepository = accessReadRepository;
    }

    public PrincipalContext principal(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken token)) {
            return null;
        }
        Jwt jwt = token.getToken();
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            return null;
        }
        PrincipalContext.PrincipalType type = principalType(jwt);
        // Resolve the system principal id; may be null for first-seen subjects.
        return new PrincipalContext(resolvePrincipalId(type, subject), type, subject);
    }

    public TenantContext tenant(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken token)) {
            return null;
        }
        Map<String, Object> claims = token.getToken().getClaims();
        UUID tenantId = uuid(claims, "tenant_id");
        UUID programId = uuid(claims, "program_id");
        if (tenantId == null && programId == null) {
            return TenantContext.SYSTEM;
        }
        return new TenantContext(tenantId, programId);
    }

    private UUID resolvePrincipalId(PrincipalContext.PrincipalType type, String subject) {
        if (type == null) {
            return null;
        }
        try {
            return accessReadRepository.findPrincipalId(type.name(), subject);
        } catch (Exception ex) {
            // DB unavailable during pre-migration phases; treat as unknown principal.
            return null;
        }
    }

    private static PrincipalContext.PrincipalType principalType(Jwt jwt) {
        Object raw = jwt.getClaim("principal_type");
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return PrincipalContext.PrincipalType.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        // Heuristic: client-credentials clients carry 'client_id' without 'sub' use.
        return jwt.getClaim("client_id") != null
                ? PrincipalContext.PrincipalType.SERVICE_ACCOUNT
                : PrincipalContext.PrincipalType.USER;
    }

    private static UUID uuid(Map<String, Object> claims, String key) {
        Object raw = claims.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID u) {
            return u;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

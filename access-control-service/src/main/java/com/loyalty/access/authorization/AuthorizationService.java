package com.loyalty.access.authorization;

import com.loyalty.access.domain.ApiPermission;
import com.loyalty.access.domain.AuthzDecision;
import com.loyalty.access.domain.EffectiveGrant;
import com.loyalty.access.domain.ScopeType;
import com.loyalty.access.repository.ApiPermissionRegistry;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.common.context.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Central authorization decision (design 27.2). Resolves the required permission for an
 * inbound (method, path), loads the principal's effective grants, and applies
 * <strong>deny by default</strong> + scope sufficiency + tenant/program context match.
 *
 * <p>Controllers never self-assemble permission checks; they delegate here. Path
 * parameters (programId/memberId/accountId) are resource locators only and may not
 * override the trusted tenant context derived from the JWT.
 */
@Service
public class AuthorizationService {

    private final ApiPermissionRegistry registry;
    private final EffectiveGrantCache grantCache;

    public AuthorizationService(ApiPermissionRegistry registry, EffectiveGrantCache grantCache) {
        this.registry = registry;
        this.grantCache = grantCache;
    }

    public AuthzDecision authorize(String method, String path, PrincipalContext principal, TenantContext tenant) {
        ApiPermission required = registry.resolve(method, path);
        if (required == null) {
            return AuthzDecision.unmapped(); // deny by default
        }
        if (principal == null) {
            return AuthzDecision.denied(required.permissionCode(), required.scopeType(), "no authenticated principal");
        }

        List<EffectiveGrant> grants = grantCache.get(principal.principalId());
        for (EffectiveGrant g : grants) {
            if (!g.permissionCode().equals(required.permissionCode())) {
                continue;
            }
            if (!ScopeType.satisfies(g.scopeType(), required.scopeType())) {
                continue; // binding scope too narrow for this API
            }
            if (contextMatches(g, tenant)) {
                return AuthzDecision.allowed(required.permissionCode(), required.scopeType());
            }
        }
        return AuthzDecision.denied(required.permissionCode(), required.scopeType(), "principal lacks permission or scope");
    }

    /** Whether a grant's tenant/program scope matches the request's trusted context. */
    static boolean contextMatches(EffectiveGrant g, TenantContext tenant) {
        return switch (g.scopeType()) {
            case SYSTEM -> true;
            case TENANT -> tenant != null && tenant.tenantId() != null
                    && tenant.tenantId().equals(g.tenantId());
            case PROGRAM -> tenant != null && tenant.tenantId() != null && tenant.programId() != null
                    && tenant.tenantId().equals(g.tenantId())
                    && tenant.programId().equals(g.programId());
        };
    }

    /** Convenience for callers that already know the principal id. */
    public boolean hasPermission(UUID principalId, String permissionCode, TenantContext tenant) {
        if (principalId == null) {
            return false;
        }
        for (EffectiveGrant g : grantCache.get(principalId)) {
            if (g.permissionCode().equals(permissionCode) && contextMatches(g, tenant)) {
                return true;
            }
        }
        return false;
    }
}

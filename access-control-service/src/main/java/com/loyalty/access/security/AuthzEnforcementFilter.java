package com.loyalty.access.security;

import com.loyalty.access.audit.AuditService;
import com.loyalty.access.authorization.AuthorizationService;
import com.loyalty.access.domain.AuthzDecision;
import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.ExecutionContext;
import com.loyalty.common.context.PrincipalContext;
import com.loyalty.common.context.TenantContext;
import com.loyalty.common.error.ApiError;
import com.loyalty.common.error.ErrorCode;
import com.loyalty.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Runs after OAuth2 resource-server authentication. Resolves the trusted principal/tenant
 * from the JWT, calls {@link AuthorizationService} (deny by default), and either proceeds
 * — binding the {@link ExecutionContext} on {@link ContextHolder} — or returns 403 and
 * records an {@code AUTHZ_DENIED} audit entry.
 */
public class AuthzEnforcementFilter extends OncePerRequestFilter {

    private final JwtContextResolver resolver;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final ObjectMapper json;

    public AuthzEnforcementFilter(JwtContextResolver resolver, AuthorizationService authz,
                                  AuditService audit, ObjectMapper json) {
        this.resolver = resolver;
        this.authz = authz;
        this.audit = audit;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth instanceof JwtAuthenticationToken)) {
            writeError(response, HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED,
                    "missing or invalid authentication");
            return;
        }

        PrincipalContext principal = resolver.principal(auth);
        TenantContext tenant = resolver.tenant(auth);
        String correlationId = ContextHolder.correlationId();

        AuthzDecision decision = authz.authorize(request.getMethod(), path, principal, tenant);
        ContextHolder.set(new ExecutionContext(tenant, principal, correlationId, null));

        if (decision.isAllowed()) {
            audit.recordAuthz("AUTHZ_ALLOWED", principal != null ? principal.principalId() : null,
                    decision.permissionCode(), path, request.getMethod(),
                    tenant != null ? tenant.tenantId() : null,
                    tenant != null ? tenant.programId() : null,
                    correlationId, correlationId, Map.of());
            chain.doFilter(request, response);
        } else {
            ErrorCode code = decision.outcome() == AuthzDecision.Outcome.UNMAPPED
                    ? ErrorCode.PERMISSION_DENIED
                    : ErrorCode.PERMISSION_DENIED;
            audit.recordAuthz("AUTHZ_DENIED", principal != null ? principal.principalId() : null,
                    decision.permissionCode(), path, request.getMethod(),
                    tenant != null ? tenant.tenantId() : null,
                    tenant != null ? tenant.programId() : null,
                    correlationId, correlationId, Map.of("reason", decision.reason()));
            writeError(response, HttpStatus.FORBIDDEN, code,
                    "permission denied: " + decision.reason());
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ErrorCode code, String message) throws IOException {
        ApiError body = ApiError.of(code, message, ContextHolder.correlationId());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(body));
    }
}

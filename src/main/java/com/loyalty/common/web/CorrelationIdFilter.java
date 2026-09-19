package com.loyalty.common.web;

import com.loyalty.common.context.ContextHolder;
import com.loyalty.common.context.ExecutionContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures every inbound HTTP request carries a correlation id on the execution context
 * and in the response header. Accepts a client-supplied {@code X-Correlation-Id} only
 * after basic validation; otherwise generates a fresh server-side id.
 *
 * <p>The {@link ExecutionContext} tenant / principal are populated later by the
 * security filter; this filter only guarantees the correlation id is present and
 * propagates it to MDC for structured logging.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        ExecutionContext existing = ContextHolder.get();
        ExecutionContext ctx = existing.withCorrelationId(correlationId);
        ContextHolder.set(ctx);
        MDC.put("correlationId", correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            ContextHolder.clear();
        }
    }

    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 200) {
            return null;
        }
        // Restrict to a conservative charset to avoid log injection / header smuggling.
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_' && c != ':') {
                return null;
            }
        }
        return trimmed;
    }
}

package com.loyalty.common.context;

import java.util.UUID;

/**
 * Snapshot of the three trust contexts so async executors, scheduled tasks and
 * Kafka listeners can carry them across threads without leaking across tenants.
 *
 * <p>Always {@link #clear()} in a finally block after use. Callers must never mutate
 * a shared instance; create a new snapshot per unit of work.
 */
public record ExecutionContext(
        TenantContext tenantContext,
        PrincipalContext principalContext,
        String correlationId,
        UUID traceId) {

    public static ExecutionContext empty() {
        return new ExecutionContext(null, null, null, null);
    }

    public ExecutionContext withCorrelationId(String correlationId) {
        return new ExecutionContext(tenantContext, principalContext, correlationId, traceId);
    }
}

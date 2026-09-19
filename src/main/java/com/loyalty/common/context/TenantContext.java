package com.loyalty.common.context;

import java.util.Objects;
import java.util.UUID;

/**
 * The tenant/program scope under which the current unit of work executes.
 *
 * <p>Held on a thread-local and propagated to async executors / Kafka consumers via
 * {@link ContextHolder}. It is set exclusively by trusted components after JWT
 * verification; request bodies and path parameters may not override it.
 */
public record TenantContext(UUID tenantId, UUID programId) {

    public static final TenantContext SYSTEM = new TenantContext(null, null);

    public TenantContext {
        // A program scope always implies a tenant scope.
        if (programId != null && tenantId == null) {
            throw new IllegalArgumentException("programId requires tenantId");
        }
    }

    public boolean isSystemScope() {
        return tenantId == null;
    }

    public boolean isProgramScope() {
        return tenantId != null && programId != null;
    }

    public boolean isTenantScope() {
        return tenantId != null && programId == null;
    }

    public TenantContext requireTenant() {
        if (tenantId == null) {
            throw new IllegalStateException("tenant scope required but none bound");
        }
        return this;
    }

    public TenantContext requireProgram() {
        if (programId == null) {
            throw new IllegalStateException("program scope required but none bound");
        }
        return this;
    }

    public UUID requireTenantId() {
        return requireTenant().tenantId;
    }

    public UUID requireProgramId() {
        return requireProgram().programId;
    }

    public static TenantContext tenant(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return new TenantContext(tenantId, null);
    }

    public static TenantContext program(UUID tenantId, UUID programId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(programId, "programId");
        return new TenantContext(tenantId, programId);
    }
}

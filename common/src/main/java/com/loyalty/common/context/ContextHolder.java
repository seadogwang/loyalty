package com.loyalty.common.context;

/**
 * Thread-local holder for the current {@link ExecutionContext}.
 *
 * <p>Intended to be set by a servlet filter / Kafka listener interceptor, and cleared
 * in a finally block. Pool-bound threads (Tomcat request threads, @Async pools,
 * Kafka consumer threads) must clear the context to prevent cross-tenant leakage.
 */
public final class ContextHolder {

    private static final ThreadLocal<ExecutionContext> HOLDER = new ThreadLocal<>();

    private ContextHolder() {
    }

    public static void set(ExecutionContext context) {
        HOLDER.set(context);
    }

    public static ExecutionContext get() {
        ExecutionContext ctx = HOLDER.get();
        return ctx == null ? ExecutionContext.empty() : ctx;
    }

    public static TenantContext tenantContext() {
        return get().tenantContext();
    }

    public static PrincipalContext principalContext() {
        return get().principalContext();
    }

    public static String correlationId() {
        return get().correlationId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

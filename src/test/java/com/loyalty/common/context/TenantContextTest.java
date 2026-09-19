package com.loyalty.common.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PROGRAM = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();

    @AfterEach
    void clear() {
        ContextHolder.clear();
    }

    @Test
    void programRequiresTenant() {
        assertThatThrownBy(() -> new TenantContext(null, PROGRAM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopePredicates() {
        assertThat(TenantContext.SYSTEM.isSystemScope()).isTrue();
        assertThat(TenantContext.tenant(TENANT).isTenantScope()).isTrue();
        assertThat(TenantContext.program(TENANT, PROGRAM).isProgramScope()).isTrue();
    }

    @Test
    void requireAccessorsThrowWithoutScope() {
        TenantContext system = TenantContext.SYSTEM;
        assertThatThrownBy(system::requireTenantId).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(system::requireProgramId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void threadLocalDoesNotLeakAcrossThreads() throws Exception {
        TenantContext ctx = TenantContext.program(TENANT, PROGRAM);
        ContextHolder.set(new ExecutionContext(ctx, null, "corr-1", null));
        assertThat(ContextHolder.tenantContext()).isEqualTo(ctx);
        assertThat(ContextHolder.correlationId()).isEqualTo("corr-1");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<ExecutionContext> seen = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            // A fresh thread must NOT inherit the bound context (no cross-tenant leakage).
            seen.set(ContextHolder.get());
            ContextHolder.set(new ExecutionContext(TenantContext.tenant(OTHER_TENANT), null, "other", null));
            started.countDown();
            done.countDown();
        });
        worker.start();
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        done.await(2, TimeUnit.SECONDS);

        assertThat(seen.get()).isEqualTo(ExecutionContext.empty());
        // The worker's set must not have leaked back into the main thread.
        assertThat(ContextHolder.tenantContext()).isEqualTo(ctx);
    }
}

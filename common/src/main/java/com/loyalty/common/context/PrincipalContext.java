package com.loyalty.common.context;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable identity of the caller derived from the trusted authentication context.
 *
 * <p>{@code tenantId} / {@code programId} are always derived from the JWT / trusted
 * security context — never accepted from request bodies or path parameters unverified.
 */
public record PrincipalContext(UUID principalId, PrincipalType principalType, String subject) {

    public PrincipalContext {
        Objects.requireNonNull(principalType, "principalType");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        // principalId may be null for a first-seen valid subject that has no role yet
        // (design M2-T01): the subject is authenticated but has zero permissions.
    }

    public enum PrincipalType {
        USER,
        SERVICE_ACCOUNT,
        API_CLIENT
    }
}

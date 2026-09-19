package com.loyalty.access.domain;

/**
 * Result of an authorization decision for a single request (design 27.2).
 */
public record AuthzDecision(
        Outcome outcome,
        String permissionCode,
        ScopeType requiredScope,
        String reason) {

    public enum Outcome { ALLOWED, DENIED, UNAUTHENTICATED, UNMAPPED }

    public static AuthzDecision allowed(String permission, ScopeType scope) {
        return new AuthzDecision(Outcome.ALLOWED, permission, scope, "allowed");
    }

    public static AuthzDecision denied(String permission, ScopeType scope, String reason) {
        return new AuthzDecision(Outcome.DENIED, permission, scope, reason);
    }

    public static AuthzDecision unmapped() {
        return new AuthzDecision(Outcome.UNMAPPED, null, null, "no api permission mapping");
    }

    public boolean isAllowed() {
        return outcome == Outcome.ALLOWED;
    }
}

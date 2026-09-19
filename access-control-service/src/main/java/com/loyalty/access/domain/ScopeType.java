package com.loyalty.access.domain;

/**
 * Authorization scope (design 27.1). SYSTEM &gt; TENANT &gt; PROGRAM; bindings are checked
 * against the request's effective scope and a role's granted scope.
 */
public enum ScopeType {
    SYSTEM,
    TENANT,
    PROGRAM;

    /**
     * Whether {@code granted} scope is sufficient to satisfy {@code required} scope.
     * SYSTEM satisfies anything; TENANT satisfies TENANT/PROGRAM; PROGRAM satisfies PROGRAM.
     */
    public static boolean satisfies(ScopeType granted, ScopeType required) {
        if (granted == required) {
            return true;
        }
        if (granted == SYSTEM) {
            return true;
        }
        if (granted == TENANT) {
            return required == PROGRAM;
        }
        return false;
    }
}

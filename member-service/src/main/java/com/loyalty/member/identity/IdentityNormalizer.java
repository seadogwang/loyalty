package com.loyalty.member.identity;

import com.loyalty.common.enums.IdentityType;

/**
 * Normalizes identity values so the active-identity unique index
 * ({@code program + type + source + normalized_value}) is stable across formatting variants
 * (design 4.6 / 16.2). EXTERNAL_ID is namespaced by identity_source — the caller passes the
 * source separately and the normalized value is not source-prefixed here; uniqueness is
 * enforced by the (type, source, normalized_value) composite.
 */
public final class IdentityNormalizer {

    private IdentityNormalizer() {}

    public static String normalize(IdentityType type, String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        return switch (type) {
            case PHONE -> normalizePhone(v);
            case EMAIL -> v.toLowerCase();
            case MEMBER_CARD, APP_USER_ID, POS_ID, EXTERNAL_ID -> v;
        };
    }

    /** Strip non-significant characters; keep a leading '+' so international numbers stay distinct. */
    static String normalizePhone(String v) {
        String trimmed = v.replaceAll("[\\s()\\-]", "");
        // Preserve a leading '+', keep digits only otherwise.
        if (trimmed.startsWith("+")) {
            return "+" + trimmed.substring(1).replaceAll("\\D", "");
        }
        return trimmed.replaceAll("\\D", "");
    }
}

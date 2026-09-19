package com.loyalty.common.web;

/**
 * Bound cursor request parsed from {@code ?cursor=} and {@code ?limit=} query params.
 * Limit is capped server-side to prevent unbounded scans (design M9-T02).
 */
public record CursorRequest(String cursor, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public CursorRequest {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }

    public static CursorRequest of(String cursor, Integer limit) {
        return new CursorRequest(cursor == null || cursor.isBlank() ? null : cursor.trim(),
                limit == null ? DEFAULT_LIMIT : limit);
    }
}

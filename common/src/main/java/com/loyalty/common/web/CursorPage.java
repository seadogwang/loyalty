package com.loyalty.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Cursor-paginated response (design 24.10). The {@code nextCursor} is opaque to clients
 * and encodes the sort key of the last returned row. Absent when there is no more data.
 */
@JsonPropertyOrder({ "items", "nextCursor", "hasMore" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CursorPage<T>(List<T> items, String nextCursor, Boolean hasMore) {

    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(items, nextCursor, nextCursor != null);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}

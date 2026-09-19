package com.loyalty.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Problem-details style error body (design 24.10). Carries the canonical {@code code},
 * a human-readable {@code message}, and the propagated {@code correlationId}.
 */
@JsonPropertyOrder({ "code", "message", "correlationId", "detail" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String correlationId, Object detail) {

    public static ApiError of(ErrorCode code, String message, String correlationId) {
        return new ApiError(code.name(), message, correlationId, null);
    }

    public static ApiError of(ErrorCode code, String message, String correlationId, Object detail) {
        return new ApiError(code.name(), message, correlationId, detail);
    }
}

package com.loyalty.common.error;

/**
 * Thrown by domain / application code for any business-rule violation that maps to a
 * canonical {@link ErrorCode}. Translated to an {@link ApiError} by the global handler.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object detail;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public ApiException(ErrorCode errorCode, String message, Object detail) {
        super(message);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object detail() {
        return detail;
    }
}

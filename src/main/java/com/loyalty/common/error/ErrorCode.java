package com.loyalty.common.error;

import org.springframework.http.HttpStatus;

/**
 * Canonical error codes and HTTP statuses from the design (section 24.9).
 *
 * <p>Each business error maps to exactly one {@link ErrorCode}; {@link HttpStatus} is
 * derived from the code so controllers never pick ad-hoc statuses.
 */
public enum ErrorCode {

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN),
    ROLE_SCOPE_VIOLATION(HttpStatus.FORBIDDEN),
    APPROVAL_REQUIRED(HttpStatus.FORBIDDEN),
    TENANT_SCOPE_VIOLATION(HttpStatus.FORBIDDEN),
    CROSS_PROGRAM_MERGE_NOT_ALLOWED(HttpStatus.FORBIDDEN),

    INVALID_REQUEST(HttpStatus.BAD_REQUEST),

    POINT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TIER_SCHEME_NOT_FOUND(HttpStatus.NOT_FOUND),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND),
    IDENTITY_NOT_FOUND(HttpStatus.NOT_FOUND),

    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT),
    ALREADY_REVERSED(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    INVALID_TIER_TRANSITION(HttpStatus.CONFLICT),
    IDENTITY_ALREADY_BOUND(HttpStatus.CONFLICT),
    IDENTITY_CONFLICT(HttpStatus.CONFLICT),
    MEMBER_ALREADY_MERGED(HttpStatus.CONFLICT),
    MERGE_TARGET_INVALID(HttpStatus.CONFLICT),
    OPERATION_IN_PROGRESS(HttpStatus.CONFLICT),
    LOCK_TIMEOUT(HttpStatus.CONFLICT),
    INVALID_ADJUSTMENT(HttpStatus.CONFLICT),
    RESTORE_NOT_ALLOWED(HttpStatus.CONFLICT),

    RULE_EVALUATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}

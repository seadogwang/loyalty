package com.loyalty.common.error;

import com.loyalty.common.context.ContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * Single translation point from thrown exceptions to {@link ApiError} bodies.
 * Never leaks SQL, stack traces, tokens or internal identifiers to clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        ErrorCode code = ex.errorCode();
        ApiError body = ApiError.of(code, ex.getMessage(), ContextHolder.correlationId(), ex.detail());
        if (code.httpStatus().is5xxServerError()) {
            log.error("api error: code={} msg={}", code, ex.getMessage(), ex);
        } else {
            log.warn("api error: code={} msg={}", code, ex.getMessage());
        }
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return status(ErrorCode.INVALID_REQUEST, "invalid request payload", detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        return status(ErrorCode.INVALID_REQUEST, "invalid request parameter", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return status(ErrorCode.INVALID_REQUEST, "invalid parameter type: " + ex.getName(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArg(IllegalArgumentException ex) {
        return status(ErrorCode.INVALID_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("unexpected error", ex);
        return status(ErrorCode.INTERNAL_ERROR, "internal error", null);
    }

    private ResponseEntity<ApiError> status(ErrorCode code, String message, Object detail) {
        ApiError body = ApiError.of(code, message, ContextHolder.correlationId(), detail);
        return ResponseEntity.status(code.httpStatus()).body(body);
    }

    // Suppress unused import warnings for HttpStatus usage kept explicit for clarity.
    @SuppressWarnings("unused")
    private HttpStatus unused() {
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

package com.booking.engine.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized error response for validation errors.
 *
 * @param timestamp   when the error occurred
 * @param status      HTTP status code
 * @param error       error type
 * @param message     general error message
 * @param fieldErrors map of field-specific errors
 * @param path        request path that caused the error
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public record ValidationErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        String path) {
    /**
     * Constructor for validation errors without path.
     */
    public ValidationErrorResponse(String message, Map<String, String> fieldErrors) {
        this(LocalDateTime.now(), 400, "Validation Error", message, fieldErrors, null);
    }
}
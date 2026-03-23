package com.booking.engine.exception;

import java.time.LocalDateTime;

/**
 * Standardized error response for general errors.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     error type
 * @param message   error message
 * @param path      request path that caused the error
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path) {
    /**
     * Constructor for simple error responses.
     */
    public ErrorResponse(int status, String error, String message) {
        this(LocalDateTime.now(), status, error, message, null);
    }
}
package com.booking.engine.exception;

/**
 * Raised when the login endpoint rate limit is exceeded.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}

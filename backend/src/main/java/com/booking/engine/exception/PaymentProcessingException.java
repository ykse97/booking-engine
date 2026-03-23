package com.booking.engine.exception;

/**
 * Exception thrown when payment operation fails.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
public class PaymentProcessingException extends RuntimeException {

    public PaymentProcessingException(String message) {
        super(message);
    }

    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}


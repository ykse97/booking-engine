package com.booking.engine.exception;

/**
 * Exception thrown when booking validation fails.
 * Used for business logic validation errors (availability, working hours,
 * etc.).
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public class BookingValidationException extends RuntimeException {

    /**
     * Constructs a new booking validation exception with the specified detail
     * message.
     *
     * @param message the detail message
     */
    public BookingValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new booking validation exception with the specified detail
     * message and cause.
     *
     * @param message the detail message
     * @param cause   the cause
     */
    public BookingValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
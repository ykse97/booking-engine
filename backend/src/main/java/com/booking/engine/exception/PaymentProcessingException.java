package com.booking.engine.exception;

/**
 * Exception thrown when payment operation fails.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
public class PaymentProcessingException extends RuntimeException {

    private final String clientMessage;

    public PaymentProcessingException(String message) {
        super(message);
        this.clientMessage = message;
    }

    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.clientMessage = message;
    }

    public PaymentProcessingException(String clientMessage, String internalMessage, Throwable cause) {
        super(internalMessage, cause);
        this.clientMessage = clientMessage;
    }

    public String getClientMessage() {
        return clientMessage;
    }
}

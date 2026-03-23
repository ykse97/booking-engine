package com.booking.engine.exception;

import java.util.UUID;

/**
 * Exception thrown when requested entity is not found in the database.
 * Returns 404 HTTP status.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public class EntityNotFoundException extends RuntimeException {

    /**
     * Constructs a new entity not found exception.
     *
     * @param entityName the name of the entity (e.g., "Barber", "Treatment")
     * @param id         the UUID that was not found
     */
    public EntityNotFoundException(String entityName, UUID id) {
        super(String.format("%s not found with id=%s", entityName, id));
    }

    /**
     * Constructs a new entity not found exception with custom message.
     *
     * @param message the detail message
     */
    public EntityNotFoundException(String message) {
        super(message);
    }
}
package com.booking.engine.service;

/**
 * Service contract for admin bootstrap operations.
 * Defines admin bootstrap related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface AdminBootstrapService {

    /**
     * Executes admin bootstrap when enabled.
     */
    void bootstrapIfEnabled();
}

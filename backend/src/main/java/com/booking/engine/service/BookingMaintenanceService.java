package com.booking.engine.service;

/**
 * Service contract for booking maintenance operations.
 * Defines booking maintenance related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface BookingMaintenanceService {

    /**
     * Applies booking and slot-hold maintenance tasks for the current business
     * time.
     */
    void updateBookingStatuses();
}

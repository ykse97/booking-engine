package com.booking.engine.entity;

/**
 * Enum representing the possible states of a booking.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public enum BookingStatus {
    /** Booking exists as an unpaid hold or is still synchronizing after Stripe payment. */
    PENDING,

    /** Booking payment is finalized and the appointment is locked into the calendar. */
    CONFIRMED,

    /** Booking cancelled before completion. */
    CANCELLED,

    /** Booking expired because the temporary unpaid hold was not completed in time. */
    EXPIRED,

    /** Booking was successfully completed and the service time is in the past. */
    DONE
}

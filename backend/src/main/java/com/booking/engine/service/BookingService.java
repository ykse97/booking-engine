package com.booking.engine.service;

/**
 * Service contract for booking operations.
 * Defines booking related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public interface BookingService extends PublicBookingService, AdminBookingService, BookingPaymentSyncService {
}

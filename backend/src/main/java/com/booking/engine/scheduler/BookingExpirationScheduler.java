package com.booking.engine.scheduler;

import com.booking.engine.service.BookingMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task for booking status transitions.
 * Runs every 5 minutes to:
 * 1) reconcile paid pending bookings into confirmed state;
 * 2) expire unpaid pending bookings that were not confirmed in time;
 * 3) complete confirmed bookings whose end time is in the past.
 * 4) physically delete old bookings older than two weeks.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private final BookingMaintenanceService bookingMaintenanceService;

    /**
     * Applies scheduled booking status transitions.
     * Executed every 5 minutes (300000 ms).
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void updateBookingStatuses() {
        bookingMaintenanceService.updateBookingStatuses();
    }
}

package com.booking.engine.scheduler;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Scheduled task for booking status transitions.
 * Runs every 5 minutes to:
 * 1) expire pending bookings that were not confirmed in time;
 * 2) complete confirmed bookings whose end time is in the past.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final BookingProperties bookingProperties;

    /**
     * Applies scheduled booking status transitions.
     * Executed every 5 minutes (300000 ms).
     */
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void updateBookingStatuses() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(bookingProperties.getTimezone()));

        List<BookingEntity> expiringBookings = bookingRepository.findByStatusAndExpiresAtBefore(
                BookingStatus.PENDING,
                now);

        for (BookingEntity booking : expiringBookings) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setExpiresAt(null);
        }

        if (!expiringBookings.isEmpty()) {
            bookingRepository.saveAll(expiringBookings);
        }

        int expired = expiringBookings.size();

        int completed = bookingRepository.completeConfirmedBookings(
                now.toLocalDate(),
                now.toLocalTime(),
                BookingStatus.CONFIRMED,
                BookingStatus.DONE);

        if (expired > 0 || completed > 0) {
            log.info("Scheduler updated bookings: expired={}, completed={}", expired, completed);
        }
    }
}

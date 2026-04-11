package com.booking.engine.service.impl;

import com.booking.engine.entity.BookingStatus;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.service.BookingMaintenanceService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Implementation of {@link BookingMaintenanceService}.
 * Provides booking maintenance related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingMaintenanceServiceImpl implements BookingMaintenanceService {

    // ---------------------- Constants ----------------------

    private static final int BOOKING_RETENTION_WEEKS = 2;

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Services ----------------------

    private final TransactionOperations transactionOperations;
    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateBookingStatuses() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(bookingProperties.getTimezone()));

        int reconciled = executeInOwnTransaction(() -> bookingRepository.reconcilePaidPendingBookings(
                BookingStatus.PENDING,
                BookingStatus.CONFIRMED,
                now));

        int expired = executeInOwnTransaction(() -> bookingRepository.expireUnpaidPendingBookings(
                BookingStatus.PENDING,
                BookingStatus.EXPIRED,
                now));

        long expiredHolds = executeInOwnTransaction(() -> slotHoldRepository.deleteByActiveTrueAndExpiresAtBefore(now));

        int completed = executeInOwnTransaction(() -> bookingRepository.completeConfirmedBookings(
                now.toLocalDate(),
                now.toLocalTime(),
                BookingStatus.CONFIRMED,
                BookingStatus.DONE));

        long deleted = executeInOwnTransaction(() -> bookingRepository.deleteByBookingDateBefore(
                now.toLocalDate().minusWeeks(BOOKING_RETENTION_WEEKS)));

        if (reconciled > 0 || expired > 0 || expiredHolds > 0 || completed > 0 || deleted > 0) {
            log.info(
                    "event=booking_maintenance action=update_statuses reconciled={} expired={} expiredHolds={} completed={} deleted={}",
                    reconciled,
                    expired,
                    expiredHolds,
                    completed,
                    deleted);
        }
    }

    // ---------------------- Private Methods ----------------------

    /**
     * Executes the supplied maintenance step in its own transaction.
     */
    private <T> T executeInOwnTransaction(Supplier<T> operation) {
        return transactionOperations.execute(status -> operation.get());
    }
}

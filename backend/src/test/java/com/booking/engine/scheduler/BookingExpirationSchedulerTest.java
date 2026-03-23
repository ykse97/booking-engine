package com.booking.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

    @Mock
    private BookingRepository bookingRepository;

    private BookingExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        scheduler = new BookingExpirationScheduler(bookingRepository, bookingProperties);
    }

    @Test
    void updateBookingStatusesShouldExpirePendingBookingsAndCompletePastConfirmedBookings() {
        BookingEntity expiringBooking = new BookingEntity();
        expiringBooking.setStatus(BookingStatus.PENDING);
        expiringBooking.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        when(bookingRepository.findByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(expiringBooking));
        when(bookingRepository.completeConfirmedBookings(any(), any(), eq(BookingStatus.CONFIRMED), eq(BookingStatus.DONE)))
                .thenReturn(1);

        scheduler.updateBookingStatuses();

        assertEquals(BookingStatus.EXPIRED, expiringBooking.getStatus());
        assertNull(expiringBooking.getExpiresAt());
        verify(bookingRepository).saveAll(List.of(expiringBooking));
        verify(bookingRepository).completeConfirmedBookings(any(), any(), eq(BookingStatus.CONFIRMED), eq(BookingStatus.DONE));
    }
}

package com.booking.engine.service.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.impl.BookingStateMachineImpl;
import com.booking.engine.service.impl.BookingValidatorImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingValidatorTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private BookingBlacklistService bookingBlacklistService;

    private BookingValidator bookingValidator;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");
        BookingStateMachine bookingStateMachine = new BookingStateMachineImpl(
                bookingRepository,
                slotHoldRepository,
                bookingProperties);
        bookingValidator = new BookingValidatorImpl(
                bookingRepository,
                slotHoldRepository,
                bookingBlacklistService,
                bookingStateMachine,
                bookingProperties);
    }

    @Test
    void validatePublicCustomerAllowedShouldReturnGenericUnavailableMessage() {
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353831234567")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validatePublicCustomerAllowed("john@example.com", "+353831234567"));

        assertEquals(
                "Booking through the website is temporarily unavailable. Please contact the barbershop directly.",
                exception.getMessage());
    }

    @Test
    void validatePendingBookingAvailabilityShouldExpireStalePendingBooking() {
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(10, 30));
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        booking.setEmployee(new EmployeeEntity());
        booking.getEmployee().setId(UUID.randomUUID());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validatePendingBookingAvailability(booking));

        assertEquals("This appointment hold has expired. Please choose another time.", exception.getMessage());
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        assertEquals(null, booking.getExpiresAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void validateHoldLimitShouldRejectWhenIpAlreadyOwnsTwoActiveHolds() {
        when(bookingRepository.countActiveUnpaidHoldsByClientIp(
                org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.eq(BookingStatus.PENDING),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(1L);
        when(slotHoldRepository.countActiveByScopeAndHoldClientIp(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(1L);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingValidator.validateHoldLimit("127.0.0.1", "device-1"));

        assertEquals(
                "This connection already has two active appointment holds. Please complete or release an existing hold before selecting another slot.",
                exception.getMessage());
    }
}

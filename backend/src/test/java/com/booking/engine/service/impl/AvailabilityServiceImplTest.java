package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.BarberDailyScheduleEntity;
import com.booking.engine.entity.BarberEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BarberDailyScheduleRepository;
import com.booking.engine.repository.BarberRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.TreatmentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AvailabilityServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BarberRepository barberRepository;

    @Mock
    private BarberDailyScheduleRepository barberScheduleRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private BookingProperties bookingProperties;

    @InjectMocks
    private AvailabilityServiceImpl availabilityService;

    private UUID barberId;
    private UUID treatmentId;
    private BookingRequestDto request;

    @BeforeEach
    void setUp() {
        barberId = UUID.randomUUID();
        treatmentId = UUID.randomUUID();

        when(bookingProperties.getTimezone()).thenReturn("Europe/Dublin");

        request = BookingRequestDto.builder()
                .barberId(barberId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .paymentMethodId("pm_card_visa")
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();
    }

    @Test
    void validateBookingRequestShouldIgnoreTreatmentDurationMismatchForFixedSlotFlow() {
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(90);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByBarberIdAndBookingDateAndStatusIn(
                barberId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> availabilityService.validateBookingRequest(request));
    }

    @Test
    void validateBookingRequestShouldFailWhenOutsideBarberHours() {
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(30);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(11, 0),
                LocalTime.of(20, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));

        assertThrows(BookingValidationException.class, () -> availabilityService.validateBookingRequest(request));
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotConflictsWithConfirmedBooking() {
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(30);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        BookingEntity existing = new BookingEntity();
        existing.setStatus(BookingStatus.CONFIRMED);
        existing.setStartTime(LocalTime.of(10, 15));
        existing.setEndTime(LocalTime.of(10, 45));

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByBarberIdAndBookingDateAndStatusIn(
                barberId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(List.of(existing));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(request));

        assertEquals("This slot has already been booked by someone else.", exception.getMessage());
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotIsHeldByAnotherGuest() {
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(30);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        BookingEntity heldBooking = new BookingEntity();
        heldBooking.setStatus(BookingStatus.PENDING);
        heldBooking.setStartTime(LocalTime.of(10, 0));
        heldBooking.setEndTime(LocalTime.of(11, 0));
        heldBooking.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        BookingRequestDto heldRequest = BookingRequestDto.builder()
                .barberId(barberId)
                .treatmentId(treatmentId)
                .bookingDate(request.getBookingDate())
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .paymentMethodId("pm_card_visa")
                .customer(request.getCustomer())
                .build();

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByBarberIdAndBookingDateAndStatusIn(
                barberId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(List.of(heldBooking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> availabilityService.validateBookingRequest(heldRequest));

        assertEquals(
                "This slot has just been held by another guest. Sorry for the inconvenience.",
                exception.getMessage());
    }

    @Test
    void validateBookingRequestShouldFailWhenSlotOverlapsBreak() {
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(60);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(13, 0),
                LocalTime.of(14, 0));

        BookingRequestDto breakRequest = BookingRequestDto.builder()
                .barberId(barberId)
                .treatmentId(treatmentId)
                .bookingDate(request.getBookingDate())
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .paymentMethodId("pm_card_visa")
                .customer(request.getCustomer())
                .build();

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));

        assertThrows(BookingValidationException.class, () -> availabilityService.validateBookingRequest(breakRequest));
    }

    @Test
    void getAvailabilityShouldReturnHourlySlotsWithBreakAndBookedStatuses() {
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(30);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                request.getBookingDate(),
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0));

        BookingEntity existing = new BookingEntity();
        existing.setStatus(BookingStatus.CONFIRMED);
        existing.setStartTime(LocalTime.of(11, 0));
        existing.setEndTime(LocalTime.of(12, 0));

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, request.getBookingDate()))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByBarberIdAndBookingDateAndStatusIn(
                barberId,
                request.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(List.of(existing));

        var result = availabilityService.getAvailability(barberId, request.getBookingDate(), treatmentId);

        assertEquals(3, result.size());
        assertEquals("AVAILABLE", result.get(0).getStatus());
        assertEquals("BREAK", result.get(1).getStatus());
        assertEquals("BOOKED", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
        assertFalse(result.get(2).isAvailable());
    }

    @Test
    void getAvailabilityShouldReturnHeldStatusForActivePendingBooking() {
        LocalDate futureDate = LocalDate.now().plusDays(2);
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(30);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                futureDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        BookingEntity heldBooking = new BookingEntity();
        heldBooking.setStatus(BookingStatus.PENDING);
        heldBooking.setStartTime(LocalTime.of(10, 0));
        heldBooking.setEndTime(LocalTime.of(11, 0));
        heldBooking.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, futureDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByBarberIdAndBookingDateAndStatusIn(
                barberId,
                futureDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(List.of(heldBooking));

        var result = availabilityService.getAvailability(barberId, futureDate, treatmentId);

        assertEquals("AVAILABLE", result.get(0).getStatus());
        assertEquals("HELD", result.get(1).getStatus());
        assertEquals("AVAILABLE", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
    }

    @Test
    void getAvailabilityShouldTreatPaidPendingBookingAsBooked() {
        LocalDate futureDate = LocalDate.now().plusDays(2);
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(30);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                futureDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        BookingEntity paidPendingBooking = new BookingEntity();
        paidPendingBooking.setStatus(BookingStatus.PENDING);
        paidPendingBooking.setStartTime(LocalTime.of(10, 0));
        paidPendingBooking.setEndTime(LocalTime.of(11, 0));
        paidPendingBooking.setExpiresAt(null);
        paidPendingBooking.setStripePaymentStatus("succeeded");
        paidPendingBooking.setPaymentCapturedAt(LocalDateTime.now());

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, futureDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByBarberIdAndBookingDateAndStatusIn(
                barberId,
                futureDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(List.of(paidPendingBooking));

        var result = availabilityService.getAvailability(barberId, futureDate, treatmentId);

        assertEquals("AVAILABLE", result.get(0).getStatus());
        assertEquals("BOOKED", result.get(1).getStatus());
        assertEquals("AVAILABLE", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
    }

    @Test
    void getAvailabilityShouldMarkEndedBookedSlotsAsPast() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        BarberEntity barber = buildActiveBarber();
        TreatmentEntity treatment = buildActiveTreatment(30);
        BarberDailyScheduleEntity hours = buildWorkingDay(
                pastDate,
                LocalTime.of(9, 0),
                LocalTime.of(12, 0),
                null,
                null);

        BookingEntity finishedBooking = new BookingEntity();
        finishedBooking.setStatus(BookingStatus.CONFIRMED);
        finishedBooking.setStartTime(LocalTime.of(10, 0));
        finishedBooking.setEndTime(LocalTime.of(11, 0));

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(barberScheduleRepository.findByBarberIdAndWorkingDate(barberId, pastDate))
                .thenReturn(Optional.of(hours));
        when(bookingRepository.findByBarberIdAndBookingDateAndStatusIn(
                barberId,
                pastDate,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED)))
                .thenReturn(List.of(finishedBooking));

        var result = availabilityService.getAvailability(barberId, pastDate, treatmentId);

        assertEquals("PAST", result.get(0).getStatus());
        assertEquals("PAST", result.get(1).getStatus());
        assertEquals("PAST", result.get(2).getStatus());
        assertFalse(result.get(1).isAvailable());
    }

    private BarberEntity buildActiveBarber() {
        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);
        barber.setActive(true);
        return barber;
    }

    private TreatmentEntity buildActiveTreatment(int durationMinutes) {
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);
        treatment.setDurationMinutes(durationMinutes);
        return treatment;
    }

    private BarberDailyScheduleEntity buildWorkingDay(
            LocalDate date,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime breakStart,
            LocalTime breakEnd) {
        BarberDailyScheduleEntity hours = new BarberDailyScheduleEntity();
        hours.setWorkingDate(date);
        hours.setWorkingDay(true);
        hours.setOpenTime(openTime);
        hours.setCloseTime(closeTime);
        hours.setBreakStartTime(breakStart);
        hours.setBreakEndTime(breakEnd);
        return hours;
    }
}

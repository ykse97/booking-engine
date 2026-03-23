package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.entity.BarberEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BarberRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.StripePaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BarberRepository barberRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private BookingBlacklistService bookingBlacklistService;

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private BookingMapper mapper;

    private BookingServiceImpl bookingService;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");

        bookingService = new BookingServiceImpl(
                bookingRepository,
                barberRepository,
                treatmentRepository,
                availabilityService,
                bookingBlacklistService,
                stripePaymentService,
                mapper,
                bookingProperties);
    }

    @Test
    void createShouldPersistPaidConfirmedBooking() {
        UUID barberId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(barberId, treatmentId);

        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);
        barber.setActive(true);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        BookingEntity bookingEntity = new BookingEntity();
        BookingEntity savedEntity = new BookingEntity();
        savedEntity.setId(bookingId);
        savedEntity.setStatus(BookingStatus.CONFIRMED);

        when(barberRepository.findByIdAndActiveTrueForUpdate(barberId)).thenReturn(Optional.of(barber));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(stripePaymentService.createAndConfirmPayment(any(), any(), any(), any())).thenReturn("pi_paid");
        when(mapper.toEntity(request)).thenReturn(bookingEntity);
        when(bookingRepository.save(bookingEntity)).thenReturn(savedEntity);
        when(mapper.toDto(savedEntity)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .stripePaymentIntentId("pi_paid")
                .build());

        BookingResponseDto result = bookingService.create(request);
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, bookingEntity.getStatus());
        verify(stripePaymentService).createAndConfirmPayment(amountCaptor.capture(), any(), any(), any());
        assertEquals(new BigDecimal("35.00"), amountCaptor.getValue());
        assertEquals(new BigDecimal("35.00"), bookingEntity.getHoldAmount());
        assertEquals("pi_paid", bookingEntity.getStripePaymentIntentId());
        assertEquals("succeeded", bookingEntity.getStripePaymentStatus());
        assertNotNull(bookingEntity.getPaymentCapturedAt());
        assertEquals(null, bookingEntity.getExpiresAt());
    }

    @Test
    void createAdminBookingShouldPersistConfirmedBookingWithoutStripe() {
        UUID barberId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .barberId(barberId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build();

        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);
        barber.setActive(true);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("28.00"));

        when(barberRepository.findByIdAndActiveTrueForUpdate(barberId)).thenReturn(Optional.of(barber));
        doNothing().when(availabilityService).validateSlotSelection(
                barberId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        doNothing().when(bookingBlacklistService).validateAllowedCustomer(null, "+353831234567");
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });
        when(mapper.toDto(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            return BookingResponseDto.builder()
                    .id(entity.getId())
                    .status(entity.getStatus())
                    .customerName(entity.getCustomerName())
                    .customerPhone(entity.getCustomerPhone())
                    .holdAmount(entity.getHoldAmount())
                    .build();
        });

        BookingResponseDto result = bookingService.createAdminBooking(request);

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("Phone Client", result.getCustomerName());
        assertEquals("+353831234567", result.getCustomerPhone());
        assertEquals(new BigDecimal("28.00"), result.getHoldAmount());

        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        BookingEntity persisted = bookingCaptor.getValue();
        assertEquals(BookingStatus.CONFIRMED, persisted.getStatus());
        assertEquals(new BigDecimal("28.00"), persisted.getHoldAmount());
        assertEquals("Phone Client", persisted.getCustomerName());
        assertEquals("+353831234567", persisted.getCustomerPhone());
        assertEquals(null, persisted.getStripePaymentIntentId());
        assertEquals(null, persisted.getStripePaymentStatus());
        assertEquals(null, persisted.getExpiresAt());
    }

    @Test
    void createShouldRejectBlacklistedCustomerWithGenericPublicMessage() {
        UUID barberId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(barberId, treatmentId);

        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);
        barber.setActive(true);

        when(barberRepository.findByIdAndActiveTrueForUpdate(barberId)).thenReturn(Optional.of(barber));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.create(request));

        assertEquals(
                "Booking through the website is temporarily unavailable. Please contact the barbershop directly.",
                exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPayment(any(), any(), any(), any());
    }

    @Test
    void holdSlotShouldPersistPendingBookingWithExpiry() {
        UUID barberId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(barberId, treatmentId);

        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);
        barber.setActive(true);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        when(barberRepository.findByIdAndActiveTrueForUpdate(barberId)).thenReturn(Optional.of(barber));
        doNothing().when(availabilityService).validateSlotSelection(
                barberId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });
        when(mapper.toDto(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            return BookingResponseDto.builder()
                    .id(entity.getId())
                    .status(entity.getStatus())
                    .expiresAt(entity.getExpiresAt())
                    .holdAmount(entity.getHoldAmount())
                    .build();
        });

        BookingResponseDto result = bookingService.holdSlot(request, "203.0.113.10", "device-123");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(result.getExpiresAt());
        assertEquals(new BigDecimal("35.00"), result.getHoldAmount());

        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        assertEquals("203.0.113.10", bookingCaptor.getValue().getHoldClientIp());
        assertEquals("device-123", bookingCaptor.getValue().getHoldClientDeviceId());
    }

    @Test
    void prepareHeldBookingCheckoutShouldCreateImmediatePaymentAndPersistCustomerDetails() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutSessionRequestDto request = BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .confirmationTokenId("ctoken_123")
                .build();

        BookingCheckoutSessionResponseDto checkoutResponse = BookingCheckoutSessionResponseDto.builder()
                .paymentIntentId("pi_checkout")
                .clientSecret("pi_checkout_secret")
                .paymentStatus("succeeded")
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(stripePaymentService.createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any()))
                .thenReturn(checkoutResponse);

        BookingCheckoutSessionResponseDto result = bookingService.prepareHeldBookingCheckout(bookingId, request);

        assertEquals("John Doe", booking.getCustomerName());
        assertEquals("john@example.com", booking.getCustomerEmail());
        assertEquals("pi_checkout", booking.getStripePaymentIntentId());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(checkoutResponse, result);
    }

    @Test
    void prepareHeldBookingCheckoutShouldRejectBlacklistedCustomer() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutSessionRequestDto request = BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("Blocked Customer")
                        .email("blocked@example.com")
                        .phone("+353870000000")
                        .build())
                .confirmationTokenId("ctoken_blocked")
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingBlacklistService.isBlockedCustomer("blocked@example.com", "+353870000000")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.prepareHeldBookingCheckout(bookingId, request));

        assertEquals(
                "Booking through the website is temporarily unavailable. Please contact the barbershop directly.",
                exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void holdSlotShouldRejectWhenSameIpAlreadyHasTwoActiveHolds() {
        UUID barberId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(barberId, treatmentId);

        when(bookingRepository.countActiveUnpaidHoldsByClientIp(
                eq("203.0.113.10"),
                eq(BookingStatus.PENDING),
                any(LocalDateTime.class))).thenReturn(2L);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals(
                "This connection already has two active appointment holds. Please complete or release an existing hold before selecting another slot.",
                exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdSlotShouldRejectWhenSameDeviceAlreadyHasTwoActiveHolds() {
        UUID barberId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(barberId, treatmentId);

        when(bookingRepository.countActiveUnpaidHoldsByClientIp(
                eq("203.0.113.10"),
                eq(BookingStatus.PENDING),
                any(LocalDateTime.class))).thenReturn(1L);
        when(bookingRepository.countActiveUnpaidHoldsByClientDeviceId(
                eq("device-123"),
                eq(BookingStatus.PENDING),
                any(LocalDateTime.class))).thenReturn(2L);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals(
                "This device already has two active appointment holds. Please complete or release an existing hold before selecting another slot.",
                exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void confirmHeldBookingShouldKeepBookingPendingUntilWebhookSync() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_confirm");

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentStatus("pi_confirm")).thenReturn("succeeded");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.PENDING)
                .build());

        BookingResponseDto result = bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                .paymentIntentId("pi_confirm")
                .build());

        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        assertEquals(BookingStatus.PENDING, result.getStatus());
    }

    @Test
    void confirmHeldBookingShouldExpireElapsedHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_expired")
                        .build()));

        assertEquals("This appointment hold has expired. Please choose another time.", exception.getMessage());
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        verify(bookingRepository).save(booking);
    }

    @Test
    void createAdminBookingShouldRejectBlacklistedPhone() {
        UUID barberId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .barberId(barberId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build();

        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);
        barber.setActive(true);

        when(barberRepository.findByIdAndActiveTrueForUpdate(barberId)).thenReturn(Optional.of(barber));
        doNothing().when(availabilityService).validateSlotSelection(
                barberId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        org.mockito.Mockito.doThrow(new BookingValidationException("This phone number is in the booking blacklist and cannot be used for appointments."))
                .when(bookingBlacklistService)
                .validateAllowedCustomer(null, "+353831234567");

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.createAdminBooking(request));

        assertEquals(
                "This phone number is in the booking blacklist and cannot be used for appointments.",
                exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void publicCancelShouldCancelUnpaidPendingHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(bookingId);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(null, booking.getExpiresAt());
    }

    @Test
    void publicCancelShouldRejectPaidPendingBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentStatus("succeeded");
        booking.setPaymentCapturedAt(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.cancelBooking(bookingId));

        assertEquals("Paid bookings can only be updated from the admin panel.", exception.getMessage());
    }

    @Test
    void cancelBookingByAdminShouldCancelPendingBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentStatus("succeeded");
        booking.setPaymentCapturedAt(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CANCELLED)
                .build());

        BookingResponseDto result = bookingService.cancelBookingByAdmin(bookingId);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(BookingStatus.CANCELLED, result.getStatus());
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldConfirmPendingBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_confirmed");
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_confirmed")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_confirmed",
                "succeeded",
                "payment_intent.succeeded");

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldStoreReleaseTimestampForFailedPayment() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_failed");

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_failed")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_failed",
                "payment_failed",
                "payment_intent.payment_failed");

        assertEquals(BookingStatus.PENDING, booking.getStatus());
        assertEquals("payment_failed", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentReleasedAt());
        verify(bookingRepository).save(booking);
    }

    private BookingEntity buildPendingBooking(UUID bookingId) {
        BarberEntity barber = new BarberEntity();
        barber.setId(UUID.randomUUID());

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());

        BookingEntity booking = new BookingEntity();
        booking.setId(bookingId);
        booking.setBarber(barber);
        booking.setTreatment(treatment);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(11, 0));
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        booking.setHoldAmount(BigDecimal.valueOf(10));
        return booking;
    }

    private BookingRequestDto buildRequest(UUID barberId, UUID treatmentId) {
        return BookingRequestDto.builder()
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

    private BookingHoldRequestDto buildHoldRequest(UUID barberId, UUID treatmentId) {
        return BookingHoldRequestDto.builder()
                .barberId(barberId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();
    }
}

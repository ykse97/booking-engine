package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.security.SecurityAuditEvent;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingAuditService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingTransactionalOperations;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.StripePaymentService;
import com.booking.engine.service.impl.BookingAuditServiceImpl;
import com.booking.engine.service.impl.BookingStateMachineImpl;
import com.booking.engine.service.impl.BookingTransactionalOperationsImpl;
import com.booking.engine.service.impl.BookingValidatorImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
    private SlotHoldRepository slotHoldRepository;

    @Mock
    private EmployeeRepository employeeRepository;

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

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    private BookingServiceImpl bookingService;

    @BeforeEach
    void setUp() {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.setTimezone("Europe/Dublin");

        BookingStateMachine bookingStateMachine = new BookingStateMachineImpl(
                bookingRepository,
                slotHoldRepository,
                bookingProperties);
        BookingValidator bookingValidator = new BookingValidatorImpl(
                bookingRepository,
                slotHoldRepository,
                bookingBlacklistService,
                bookingStateMachine,
                bookingProperties);
        BookingTransactionalOperations bookingTransactionalOperations = new BookingTransactionalOperationsImpl(
                bookingRepository,
                slotHoldRepository,
                employeeRepository,
                treatmentRepository,
                availabilityService,
                bookingValidator,
                bookingStateMachine,
                bookingProperties);
        BookingAuditService bookingAuditService = new BookingAuditServiceImpl(securityAuditLogger);

        bookingService = new BookingServiceImpl(
                bookingRepository,
                slotHoldRepository,
                employeeRepository,
                treatmentRepository,
                mapper,
                availabilityService,
                bookingBlacklistService,
                stripePaymentService,
                bookingValidator,
                bookingStateMachine,
                bookingTransactionalOperations,
                bookingAuditService,
                bookingProperties);

        org.mockito.Mockito.lenient().when(slotHoldRepository.findByIdForUpdate(any(UUID.class)))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(slotHoldRepository.findByStripePaymentIntentIdForUpdate(any(String.class)))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                any(UUID.class),
                any(LocalDate.class),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(slotHoldRepository.findActiveByScopeAndHoldClientDeviceId(
                any(),
                any(String.class),
                any(LocalDateTime.class)))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(slotHoldRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(securityAuditLogger.event(anyString(), anyString()))
                .thenAnswer(invocation -> SecurityAuditEvent.builder()
                        .eventType(invocation.getArgument(0))
                        .outcome(invocation.getArgument(1)));
    }

    @Test
    void createShouldPersistPaidConfirmedBooking() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));
        AtomicReference<SlotHoldEntity> reservedSlotHoldRef = new AtomicReference<>();

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(slotHoldId);
            }
            reservedSlotHoldRef.set(entity);
            return entity;
        });
        when(slotHoldRepository.findByIdForUpdate(slotHoldId))
                .thenAnswer(invocation -> Optional.ofNullable(reservedSlotHoldRef.get()));
        when(stripePaymentService.createAndConfirmPayment(any(), any(), any(), any())).thenReturn("pi_paid");
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
                    .stripePaymentIntentId(entity.getStripePaymentIntentId())
                    .build();
        });

        BookingResponseDto result = bookingService.create(request);
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("pi_paid", result.getStripePaymentIntentId());
        verify(stripePaymentService).createAndConfirmPayment(
                amountCaptor.capture(),
                any(),
                any(),
                metadataCaptor.capture());
        verify(bookingRepository).save(bookingCaptor.capture());
        assertEquals(new BigDecimal("35.00"), amountCaptor.getValue());
        assertEquals(slotHoldId.toString(), metadataCaptor.getValue().get("slotHoldId"));
        assertEquals(request.getEmployeeId().toString(), metadataCaptor.getValue().get("employeeId"));
        assertEquals(request.getTreatmentId().toString(), metadataCaptor.getValue().get("treatmentId"));
        assertEquals(request.getBookingDate().toString(), metadataCaptor.getValue().get("bookingDate"));
        assertEquals(request.getStartTime().toString(), metadataCaptor.getValue().get("startTime"));
        assertEquals(request.getEndTime().toString(), metadataCaptor.getValue().get("endTime"));
        org.junit.jupiter.api.Assertions.assertFalse(metadataCaptor.getValue().containsKey("customerEmail"));
        assertEquals("pi_paid", reservedSlotHoldRef.get().getStripePaymentIntentId());
        assertEquals("succeeded", reservedSlotHoldRef.get().getStripePaymentStatus());
        assertNotNull(reservedSlotHoldRef.get().getPaymentCapturedAt());

        BookingEntity persistedBooking = bookingCaptor.getValue();
        assertEquals(BookingStatus.CONFIRMED, persistedBooking.getStatus());
        assertEquals("John Doe", persistedBooking.getCustomerName());
        assertEquals("john@example.com", persistedBooking.getCustomerEmail());
        assertEquals("+353870000000", persistedBooking.getCustomerPhone());
        assertEquals(new BigDecimal("35.00"), persistedBooking.getHoldAmount());
        assertEquals("pi_paid", persistedBooking.getStripePaymentIntentId());
        assertEquals("succeeded", persistedBooking.getStripePaymentStatus());
        assertNotNull(persistedBooking.getPaymentCapturedAt());
        assertEquals(null, persistedBooking.getExpiresAt());
        verify(slotHoldRepository).delete(reservedSlotHoldRef.get());
    }

    @Test
    void createShouldReleaseReservedSlotHoldWhenStripePaymentFailsBeforePaymentIntentExists() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID slotHoldId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));
        AtomicReference<SlotHoldEntity> reservedSlotHoldRef = new AtomicReference<>();

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(slotHoldId);
            }
            reservedSlotHoldRef.set(entity);
            return entity;
        });
        when(slotHoldRepository.findByIdForUpdate(slotHoldId))
                .thenAnswer(invocation -> Optional.ofNullable(reservedSlotHoldRef.get()));
        when(stripePaymentService.createAndConfirmPayment(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("stripe unavailable"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> bookingService.create(request));

        assertEquals("stripe unavailable", exception.getMessage());
        verify(slotHoldRepository).delete(reservedSlotHoldRef.get());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void createShouldKeepReservedSlotHoldForWebhookReconciliationWhenFinalizationFailsAfterPaymentPersistence() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID slotHoldId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));
        AtomicReference<SlotHoldEntity> reservedSlotHoldRef = new AtomicReference<>();

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateBookingRequest(request);
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(slotHoldId);
            }
            reservedSlotHoldRef.set(entity);
            return entity;
        });
        when(slotHoldRepository.findByIdForUpdate(slotHoldId))
                .thenAnswer(invocation -> Optional.ofNullable(reservedSlotHoldRef.get()));
        when(stripePaymentService.createAndConfirmPayment(any(), any(), any(), any())).thenReturn("pi_paid");
        when(bookingRepository.save(any(BookingEntity.class))).thenThrow(new RuntimeException("database write failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> bookingService.create(request));

        assertEquals("database write failed", exception.getMessage());
        assertEquals("pi_paid", reservedSlotHoldRef.get().getStripePaymentIntentId());
        assertEquals("succeeded", reservedSlotHoldRef.get().getStripePaymentStatus());
        assertNotNull(reservedSlotHoldRef.get().getPaymentCapturedAt());
        verify(slotHoldRepository, never()).delete(any(SlotHoldEntity.class));
    }

    @Test
    void createAdminBookingShouldPersistConfirmedBookingWithoutStripe() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .customerEmail("phone@example.com")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("28.00"));

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("phone@example.com", "+353831234567");
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
                    .customerEmail(entity.getCustomerEmail())
                    .holdAmount(entity.getHoldAmount())
                    .build();
        });

        BookingResponseDto result = bookingService.createAdminBooking(request);

        assertEquals(bookingId, result.getId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
        assertEquals("Phone Client", result.getCustomerName());
        assertEquals("+353831234567", result.getCustomerPhone());
        assertEquals("phone@example.com", result.getCustomerEmail());
        assertEquals(new BigDecimal("28.00"), result.getHoldAmount());

        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        BookingEntity persisted = bookingCaptor.getValue();
        assertEquals(BookingStatus.CONFIRMED, persisted.getStatus());
        assertEquals(new BigDecimal("28.00"), persisted.getHoldAmount());
        assertEquals("Phone Client", persisted.getCustomerName());
        assertEquals("+353831234567", persisted.getCustomerPhone());
        assertEquals("phone@example.com", persisted.getCustomerEmail());
        assertEquals(null, persisted.getStripePaymentIntentId());
        assertEquals(null, persisted.getStripePaymentStatus());
        assertEquals(null, persisted.getExpiresAt());
        verify(securityAuditLogger).log(org.mockito.ArgumentMatchers.argThat(event ->
                "ADMIN_BOOKING_CREATE".equals(event.getEventType())
                        && bookingId.toString().equals(event.getResourceId())));
    }

    @Test
    void createAdminBookingShouldPromoteMatchingAdminHoldIntoConfirmedBooking() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .holdBookingId(holdId)
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .customerEmail("phone@example.com")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("28.00"));

        BookingEntity heldBooking = buildPendingBooking(holdId);
        heldBooking.setActive(false);
        heldBooking.setEmployee(employee);
        heldBooking.setTreatment(treatment);
        heldBooking.setBookingDate(request.getBookingDate());
        heldBooking.setStartTime(request.getStartTime());
        heldBooking.setEndTime(request.getEndTime());
        heldBooking.setHoldClientIp("admin-panel");
        heldBooking.setHoldClientDeviceId("admin-panel:session-123");

        when(bookingRepository.findByIdForUpdate(holdId)).thenReturn(Optional.of(heldBooking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                eq(employeeId),
                eq(request.getBookingDate()),
                eq(List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE))))
                .thenReturn(List.of(heldBooking));
        doNothing().when(availabilityService).validateSlotSelectionExcludingBooking(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                holdId);
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("phone@example.com", "+353831234567");
        when(bookingRepository.save(heldBooking)).thenReturn(heldBooking);
        when(mapper.toDto(heldBooking)).thenReturn(BookingResponseDto.builder()
                .id(holdId)
                .status(BookingStatus.CONFIRMED)
                .customerName("Phone Client")
                .build());

        BookingResponseDto result = bookingService.createAdminBooking(request, "session-123");

        assertEquals(BookingStatus.CONFIRMED, heldBooking.getStatus());
        assertEquals(Boolean.TRUE, heldBooking.getActive());
        assertEquals("Phone Client", heldBooking.getCustomerName());
        assertEquals("+353831234567", heldBooking.getCustomerPhone());
        assertEquals("phone@example.com", heldBooking.getCustomerEmail());
        assertEquals(new BigDecimal("28.00"), heldBooking.getHoldAmount());
        assertEquals(null, heldBooking.getExpiresAt());
        assertEquals(null, heldBooking.getHoldClientIp());
        assertEquals(null, heldBooking.getHoldClientDeviceId());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void findLatestCustomerByPhoneShouldReturnLatestExactMatch() {
        BookingEntity newestMatch = new BookingEntity();
        newestMatch.setCustomerName("Repeat Client");
        newestMatch.setCustomerPhone("+353 83 123 4567");
        newestMatch.setCustomerEmail("repeat@example.com");

        BookingEntity otherBooking = new BookingEntity();
        otherBooking.setCustomerName("Someone Else");
        otherBooking.setCustomerPhone("+353 85 000 0000");

        when(bookingRepository.findAllByActiveTrueAndCustomerPhoneIsNotNullOrderByCreatedAtDesc())
                .thenReturn(java.util.List.of(newestMatch, otherBooking));

        AdminBookingCustomerLookupResponseDto result = bookingService.findLatestCustomerByPhone("+353831234567")
                .orElseThrow();

        assertEquals("Repeat Client", result.getCustomerName());
        assertEquals("+353 83 123 4567", result.getCustomerPhone());
        assertEquals("repeat@example.com", result.getCustomerEmail());
    }

    @Test
    void findLatestCustomerByPhoneShouldReturnEmptyForBlankOrUnknownPhone() {
        when(bookingRepository.findAllByActiveTrueAndCustomerPhoneIsNotNullOrderByCreatedAtDesc())
                .thenReturn(java.util.List.of());

        assertEquals(true, bookingService.findLatestCustomerByPhone("   ").isEmpty());
        assertEquals(true, bookingService.findLatestCustomerByPhone("+353000000000").isEmpty());
    }

    @Test
    void createShouldRejectBlacklistedCustomerWithGenericPublicMessage() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
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
    void createShouldPropagateUnsupportedEmployeeTreatmentValidation() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingRequestDto request = buildRequest(employeeId, treatmentId);
        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        org.mockito.Mockito
                .doThrow(new BookingValidationException("This employee does not provide the selected service."))
                .when(availabilityService)
                .validateBookingRequest(request);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.create(request));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdSlotShouldPersistPendingBookingWithExpiry() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        BookingResponseDto result = bookingService.holdSlot(request, "203.0.113.10", "device-123");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(result.getExpiresAt());
        assertEquals(new BigDecimal("35.00"), result.getHoldAmount());

        ArgumentCaptor<SlotHoldEntity> holdCaptor = ArgumentCaptor.forClass(SlotHoldEntity.class);
        verify(slotHoldRepository).save(holdCaptor.capture());
        assertEquals("203.0.113.10", holdCaptor.getValue().getHoldClientIp());
        assertEquals("device-123", holdCaptor.getValue().getHoldClientDeviceId());
    }

    @Test
    void holdAdminSlotShouldPersistInactivePendingBookingWithAdminSession() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        when(bookingRepository.findByHoldClientDeviceIdAndStatus("admin-panel:session-123", BookingStatus.PENDING))
                .thenReturn(List.of());
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        BookingResponseDto result = bookingService.holdAdminSlot(request, "session-123");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(result.getExpiresAt());
        assertEquals(new BigDecimal("35.00"), result.getHoldAmount());

        ArgumentCaptor<SlotHoldEntity> holdCaptor = ArgumentCaptor.forClass(SlotHoldEntity.class);
        verify(slotHoldRepository).save(holdCaptor.capture());
        assertEquals(Boolean.TRUE, holdCaptor.getValue().getActive());
        assertEquals("admin-panel", holdCaptor.getValue().getHoldClientIp());
        assertEquals("admin-panel:session-123", holdCaptor.getValue().getHoldClientDeviceId());
    }

    @Test
    void holdAdminSlotShouldReleasePreviousAdminSessionSlotHoldBeforeSavingNewOne() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        SlotHoldEntity existingHold = new SlotHoldEntity();
        existingHold.setId(UUID.randomUUID());
        existingHold.setActive(true);
        existingHold.setHoldScope(SlotHoldScope.ADMIN);
        existingHold.setHoldClientDeviceId("admin-panel:session-123");
        existingHold.setStartTime(LocalTime.of(9, 0));
        existingHold.setEndTime(LocalTime.of(10, 0));

        when(slotHoldRepository.findActiveByScopeAndHoldClientDeviceId(
                eq(SlotHoldScope.ADMIN),
                eq("admin-panel:session-123"),
                any(LocalDateTime.class)))
                .thenReturn(List.of(existingHold));
        when(bookingRepository.findByHoldClientDeviceIdAndStatus("admin-panel:session-123", BookingStatus.PENDING))
                .thenReturn(List.of());
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        when(treatmentRepository.findById(treatmentId)).thenReturn(Optional.of(treatment));
        when(slotHoldRepository.save(any(SlotHoldEntity.class))).thenAnswer(invocation -> {
            SlotHoldEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        bookingService.holdAdminSlot(request, "session-123");

        verify(slotHoldRepository).delete(existingHold);
        verify(slotHoldRepository).save(any(SlotHoldEntity.class));
    }

    @Test
    void refreshAdminHoldShouldExtendPendingAdminSessionHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setActive(false);
        booking.setHoldClientIp("admin-panel");
        booking.setHoldClientDeviceId("admin-panel:session-123");
        LocalDateTime previousExpiry = LocalDateTime.now().plusSeconds(30);
        booking.setExpiresAt(previousExpiry);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                eq(booking.getEmployee().getId()),
                eq(booking.getBookingDate()),
                eq(List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE))))
                .thenReturn(List.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.PENDING)
                .expiresAt(booking.getExpiresAt())
                .build());

        BookingResponseDto result = bookingService.refreshAdminHold(bookingId, "session-123");

        assertEquals(BookingStatus.PENDING, result.getStatus());
        assertNotNull(booking.getExpiresAt());
        org.junit.jupiter.api.Assertions.assertTrue(booking.getExpiresAt().isAfter(previousExpiry));
    }

    @Test
    void releaseAdminHoldShouldCancelAndDeactivateMatchingPendingAdminHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setActive(false);
        booking.setHoldClientIp("admin-panel");
        booking.setHoldClientDeviceId("admin-panel:session-123");

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.releaseAdminHold(bookingId, "session-123");

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Boolean.FALSE, booking.getActive());
        assertEquals(null, booking.getExpiresAt());
        assertEquals(null, booking.getHoldClientIp());
        assertEquals(null, booking.getHoldClientDeviceId());
    }

    @Test
    void holdSlotShouldRejectNonBookableEmployee() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        employee.setBookable(false);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals("Employee is not available for booking", exception.getMessage());
        verify(availabilityService, never()).validateSlotSelection(any(), any(), any(), any(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void holdSlotShouldRejectUnsupportedEmployeeTreatmentPair() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);
        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        org.mockito.Mockito
                .doThrow(new BookingValidationException("This employee does not provide the selected service."))
                .when(availabilityService)
                .validateSlotSelection(
                        employeeId,
                        treatmentId,
                        request.getBookingDate(),
                        request.getStartTime(),
                        request.getEndTime());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.holdSlot(request, "203.0.113.10", "device-123"));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void validateHeldBookingCheckoutShouldAllowEligibleCustomerBeforeOpeningStripe() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutValidationRequestDto request = BookingCheckoutValidationRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);

        bookingService.validateHeldBookingCheckout(bookingId, request);

        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void validateHeldBookingCheckoutShouldRejectBlacklistedCustomerBeforeStripe() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingCheckoutValidationRequestDto request = BookingCheckoutValidationRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("Blocked Customer")
                        .email("blocked@example.com")
                        .phone("+353870000000")
                        .build())
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingBlacklistService.isBlockedCustomer("blocked@example.com", "+353870000000")).thenReturn(true);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.validateHeldBookingCheckout(bookingId, request));

        assertEquals(
                "Booking through the website is temporarily unavailable. Please contact the barbershop directly.",
                exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
    }

    @Test
    void validateHeldBookingCheckoutShouldRejectWhenSlotWasTakenByDoneBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingEntity conflictingBooking = buildConflictingBooking(booking, BookingStatus.DONE);
        BookingCheckoutValidationRequestDto request = BookingCheckoutValidationRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                booking.getEmployee().getId(),
                booking.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(conflictingBooking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.validateHeldBookingCheckout(bookingId, request));

        assertEquals("This slot has already been booked by someone else.", exception.getMessage());
        verify(stripePaymentService, never()).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any());
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
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

        assertEquals("John Doe", booking.getCustomerName());
        assertEquals("john@example.com", booking.getCustomerEmail());
        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("pi_checkout", booking.getStripePaymentIntentId());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        verify(stripePaymentService).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), metadataCaptor.capture());
        assertEquals(bookingId.toString(), metadataCaptor.getValue().get("bookingId"));
        assertEquals(booking.getEmployee().getId().toString(), metadataCaptor.getValue().get("employeeId"));
        assertEquals(booking.getTreatment().getId().toString(), metadataCaptor.getValue().get("treatmentId"));
        org.junit.jupiter.api.Assertions.assertFalse(metadataCaptor.getValue().containsKey("customerEmail"));
        assertEquals(checkoutResponse, result);
    }

    @Test
    void prepareHeldBookingCheckoutShouldUseNonPiiMetadataForSlotHoldFlow() {
        UUID slotHoldId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setPrice(new BigDecimal("35.00"));

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setActive(true);
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(LocalDate.now().plusDays(1));
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(10, 30));
        slotHold.setHoldAmount(new BigDecimal("35.00"));
        slotHold.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        BookingCheckoutSessionRequestDto request = BookingCheckoutSessionRequestDto.builder()
                .customer(BookingRequestDto.CustomerDetailsDto.builder()
                        .name("John Doe")
                        .email("john@example.com")
                        .phone("+353870000000")
                        .build())
                .confirmationTokenId("ctoken_123")
                .build();

        BookingCheckoutSessionResponseDto checkoutResponse = BookingCheckoutSessionResponseDto.builder()
                .paymentIntentId("pi_slot_hold")
                .clientSecret("pi_slot_hold_secret")
                .paymentStatus("succeeded")
                .build();

        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(bookingBlacklistService.isBlockedCustomer("john@example.com", "+353870000000")).thenReturn(false);
        when(stripePaymentService.createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), any()))
                .thenReturn(checkoutResponse);

        BookingCheckoutSessionResponseDto result = bookingService.prepareHeldBookingCheckout(slotHoldId, request);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> metadataCaptor = ArgumentCaptor.forClass(Map.class);

        verify(stripePaymentService).createAndConfirmPaymentWithConfirmationToken(any(), any(), any(), metadataCaptor.capture());
        assertEquals(slotHoldId.toString(), metadataCaptor.getValue().get("slotHoldId"));
        assertEquals(employeeId.toString(), metadataCaptor.getValue().get("employeeId"));
        assertEquals(treatmentId.toString(), metadataCaptor.getValue().get("treatmentId"));
        org.junit.jupiter.api.Assertions.assertFalse(metadataCaptor.getValue().containsKey("customerEmail"));
        assertEquals("john@example.com", slotHold.getCustomerEmail());
        assertEquals("pi_slot_hold", slotHold.getStripePaymentIntentId());
        assertEquals("succeeded", slotHold.getStripePaymentStatus());
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
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

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
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        BookingHoldRequestDto request = buildHoldRequest(employeeId, treatmentId);

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
    void confirmHeldBookingShouldConfirmSucceededPaymentImmediately() {
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
                .status(BookingStatus.CONFIRMED)
                .build());

        BookingResponseDto result = bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                .paymentIntentId("pi_confirm")
                .build());

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void confirmHeldBookingShouldConfirmEvenIfHoldJustExpiredAfterStripeSucceeded() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_expired_but_paid");
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentStatus("pi_expired_but_paid")).thenReturn("succeeded");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .build());

        BookingResponseDto result = bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                .paymentIntentId("pi_expired_but_paid")
                .build());

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertNotNull(booking.getPaymentCapturedAt());
        assertEquals(null, booking.getExpiresAt());
        assertEquals(BookingStatus.CONFIRMED, result.getStatus());
    }

    @Test
    void confirmHeldBookingShouldExpireElapsedHold() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_expired");
        booking.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentStatus("pi_expired")).thenReturn("requires_payment_method");

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
    void confirmHeldBookingShouldRejectWhenSlotWasTakenByDoneBooking() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        BookingEntity conflictingBooking = buildConflictingBooking(booking, BookingStatus.DONE);
        booking.setCustomerName("John Doe");
        booking.setCustomerEmail("john@example.com");
        booking.setStripePaymentIntentId("pi_slot_taken");

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(stripePaymentService.getPaymentIntentStatus("pi_slot_taken")).thenReturn("succeeded");
        when(bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                booking.getEmployee().getId(),
                booking.getBookingDate(),
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE)))
                .thenReturn(List.of(conflictingBooking));

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.confirmHeldBooking(bookingId, BookingConfirmationRequestDto.builder()
                        .paymentIntentId("pi_slot_taken")
                        .build()));

        assertEquals("This slot has already been booked by someone else.", exception.getMessage());
        verify(bookingRepository, never()).save(any(BookingEntity.class));
    }

    @Test
    void createAdminBookingShouldRejectBlacklistedPhone() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(availabilityService).validateSlotSelection(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        org.mockito.Mockito
                .doThrow(new BookingValidationException(
                        "This phone number is in the booking blacklist and cannot be used for appointments."))
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
    void createAdminBookingShouldRejectUnsupportedEmployeeTreatmentPair() {
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();
        AdminBookingCreateRequestDto request = AdminBookingCreateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(14, 0))
                .customerName("Phone Client")
                .customerPhone("+353831234567")
                .build();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);

        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        org.mockito.Mockito
                .doThrow(new BookingValidationException("This employee does not provide the selected service."))
                .when(availabilityService)
                .validateSlotSelection(
                        employeeId,
                        treatmentId,
                        request.getBookingDate(),
                        request.getStartTime(),
                        request.getEndTime());

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.createAdminBooking(request));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
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
        assertEquals(Boolean.TRUE, booking.getSlotLocked());
        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(securityAuditLogger).log(org.mockito.ArgumentMatchers.argThat(event ->
                "ADMIN_BOOKING_CANCEL".equals(event.getEventType())
                        && "PENDING".equals(event.getAdditionalFields().get("oldStatus"))
                        && "CANCELLED".equals(event.getAdditionalFields().get("newStatus"))));
    }

    @Test
    void updateBookingByAdminShouldPersistEditableBookingFields() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSlotLocked(false);
        booking.setCustomerName("John Doe");
        booking.setCustomerPhone("+353870000000");
        booking.setCustomerEmail("john@example.com");
        booking.getEmployee().setId(employeeId);
        booking.getTreatment().setId(treatmentId);

        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(booking.getBookingDate())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .customerName("Jane Doe")
                .customerPhone("+353871111111")
                .customerEmail("jane@example.com")
                .holdAmount(new BigDecimal("45.00"))
                .status(BookingStatus.CANCELLED)
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatmentId)).thenReturn(Optional.of(treatment));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("jane@example.com", "+353871111111");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CANCELLED)
                .customerName("Jane Doe")
                .customerPhone("+353871111111")
                .customerEmail("jane@example.com")
                .holdAmount(new BigDecimal("45.00"))
                .build());

        BookingResponseDto result = bookingService.updateBookingByAdmin(bookingId, request);

        assertEquals("Jane Doe", booking.getCustomerName());
        assertEquals("+353871111111", booking.getCustomerPhone());
        assertEquals("jane@example.com", booking.getCustomerEmail());
        assertEquals(new BigDecimal("45.00"), booking.getHoldAmount());
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(Boolean.TRUE, booking.getSlotLocked());
        verify(availabilityService, never()).validateSlotSelectionExcludingBooking(any(), any(), any(), any(), any(),
                any());
        assertEquals(BookingStatus.CANCELLED, result.getStatus());
        verify(securityAuditLogger).log(org.mockito.ArgumentMatchers.argThat(event ->
                "ADMIN_BOOKING_UPDATE".equals(event.getEventType())
                        && bookingId.toString().equals(event.getResourceId())
                        && "CONFIRMED".equals(event.getAdditionalFields().get("oldStatus"))
                        && "CANCELLED".equals(event.getAdditionalFields().get("newStatus"))));
    }

    @Test
    void updateBookingByAdminShouldValidateMovedSlotExcludingCurrentBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCustomerName("John Doe");

        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(booking.getBookingDate().plusDays(1))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .customerName("John Doe")
                .customerPhone("+353870000000")
                .customerEmail("john@example.com")
                .holdAmount(new BigDecimal("10.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatmentId)).thenReturn(Optional.of(treatment));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("john@example.com", "+353870000000");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.CONFIRMED)
                .build());

        bookingService.updateBookingByAdmin(bookingId, request);

        verify(availabilityService).validateSlotSelectionExcludingBooking(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                bookingId);
    }

    @Test
    void updateBookingByAdminShouldValidateMovedDoneBookingSlotExcludingCurrentBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setCustomerName("John Doe");

        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(booking.getBookingDate().plusDays(1))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .customerName("John Doe")
                .customerPhone("+353870000000")
                .customerEmail("john@example.com")
                .holdAmount(new BigDecimal("10.00"))
                .status(BookingStatus.DONE)
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatmentId)).thenReturn(Optional.of(treatment));
        doNothing().when(bookingBlacklistService).validateAllowedCustomer("john@example.com", "+353870000000");
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(mapper.toDto(booking)).thenReturn(BookingResponseDto.builder()
                .id(bookingId)
                .status(BookingStatus.DONE)
                .build());

        bookingService.updateBookingByAdmin(bookingId, request);

        verify(availabilityService).validateSlotSelectionExcludingBooking(
                employeeId,
                treatmentId,
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                bookingId);
    }

    @Test
    void updateBookingByAdminShouldRejectUnsupportedEmployeeTreatmentPair() {
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);
        treatment.setActive(true);

        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);

        AdminBookingUpdateRequestDto request = AdminBookingUpdateRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(booking.getBookingDate().plusDays(1))
                .startTime(LocalTime.of(12, 0))
                .endTime(LocalTime.of(13, 0))
                .customerName("John Doe")
                .customerPhone("+353870000000")
                .customerEmail("john@example.com")
                .holdAmount(new BigDecimal("10.00"))
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)).thenReturn(Optional.of(employee));
        when(treatmentRepository.findByIdAndActiveTrue(treatmentId)).thenReturn(Optional.of(treatment));
        org.mockito.Mockito
                .doThrow(new BookingValidationException("This employee does not provide the selected service."))
                .when(availabilityService)
                .validateSlotSelectionExcludingBooking(
                        employeeId,
                        treatmentId,
                        request.getBookingDate(),
                        request.getStartTime(),
                        request.getEndTime(),
                        bookingId);

        BookingValidationException exception = assertThrows(
                BookingValidationException.class,
                () -> bookingService.updateBookingByAdmin(bookingId, request));

        assertEquals("This employee does not provide the selected service.", exception.getMessage());
        verify(bookingRepository, never()).save(any());
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
    void syncStripePaymentIntentFromWebhookShouldKeepConfirmedBookingStableOnRepeatedSuccess() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_repeated");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setStripePaymentStatus("succeeded");
        LocalDateTime capturedAt = LocalDateTime.now().minusMinutes(2);
        booking.setPaymentCapturedAt(capturedAt);

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_repeated")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_repeated",
                "succeeded",
                "payment_intent.succeeded");

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals("succeeded", booking.getStripePaymentStatus());
        assertSame(capturedAt, booking.getPaymentCapturedAt());
        verify(bookingRepository).save(booking);
    }

    @Test
    void syncStripePaymentIntentFromWebhookShouldConfirmExpiredBookingAfterSuccessfulPayment() {
        UUID bookingId = UUID.randomUUID();
        BookingEntity booking = buildPendingBooking(bookingId);
        booking.setStripePaymentIntentId("pi_late_webhook");
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setExpiresAt(null);

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_late_webhook"))
                .thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_late_webhook",
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

    @Test
    void syncStripePaymentIntentFromWebhookShouldRecoverSlotHoldUsingMetadataWhenPaymentIntentWasNotPersisted() {
        UUID slotHoldId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID treatmentId = UUID.randomUUID();

        EmployeeEntity employee = buildActiveBookableEmployee(employeeId);
        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(treatmentId);

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setId(slotHoldId);
        slotHold.setActive(true);
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setCustomerName("John Doe");
        slotHold.setCustomerEmail("john@example.com");
        slotHold.setCustomerPhone("+353870000000");
        slotHold.setBookingDate(LocalDate.now().plusDays(1));
        slotHold.setStartTime(LocalTime.of(10, 0));
        slotHold.setEndTime(LocalTime.of(10, 30));
        slotHold.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        slotHold.setHoldAmount(new BigDecimal("35.00"));

        when(bookingRepository.findByStripePaymentIntentIdForUpdate("pi_metadata")).thenReturn(Optional.empty());
        when(slotHoldRepository.findByStripePaymentIntentIdForUpdate("pi_metadata")).thenReturn(Optional.empty());
        when(slotHoldRepository.findByIdForUpdate(slotHoldId)).thenReturn(Optional.of(slotHold));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(invocation -> {
            BookingEntity entity = invocation.getArgument(0);
            entity.setId(bookingId);
            return entity;
        });

        bookingService.syncStripePaymentIntentFromWebhook(
                "pi_metadata",
                "succeeded",
                "payment_intent.succeeded",
                Map.of("slotHoldId", slotHoldId.toString()));

        ArgumentCaptor<BookingEntity> bookingCaptor = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        verify(slotHoldRepository).delete(slotHold);

        BookingEntity persistedBooking = bookingCaptor.getValue();
        assertEquals(bookingId, persistedBooking.getId());
        assertEquals(BookingStatus.CONFIRMED, persistedBooking.getStatus());
        assertEquals("John Doe", persistedBooking.getCustomerName());
        assertEquals("john@example.com", persistedBooking.getCustomerEmail());
        assertEquals("+353870000000", persistedBooking.getCustomerPhone());
        assertEquals(slotHold.getBookingDate(), persistedBooking.getBookingDate());
        assertEquals(slotHold.getStartTime(), persistedBooking.getStartTime());
        assertEquals(slotHold.getEndTime(), persistedBooking.getEndTime());
        assertEquals(new BigDecimal("35.00"), persistedBooking.getHoldAmount());
        assertEquals("pi_metadata", persistedBooking.getStripePaymentIntentId());
        assertEquals("succeeded", persistedBooking.getStripePaymentStatus());
        assertNotNull(persistedBooking.getPaymentCapturedAt());
        assertEquals("pi_metadata", slotHold.getStripePaymentIntentId());
    }

    private BookingEntity buildPendingBooking(UUID bookingId) {
        EmployeeEntity employee = buildActiveBookableEmployee(UUID.randomUUID());

        TreatmentEntity treatment = new TreatmentEntity();
        treatment.setId(UUID.randomUUID());

        BookingEntity booking = new BookingEntity();
        booking.setId(bookingId);
        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingDate(LocalDate.now().plusDays(1));
        booking.setStartTime(LocalTime.of(10, 0));
        booking.setEndTime(LocalTime.of(11, 0));
        booking.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        booking.setHoldAmount(BigDecimal.valueOf(10));
        booking.setSlotLocked(false);
        return booking;
    }

    private BookingEntity buildConflictingBooking(BookingEntity booking, BookingStatus status) {
        BookingEntity conflictingBooking = new BookingEntity();
        conflictingBooking.setId(UUID.randomUUID());
        conflictingBooking.setEmployee(booking.getEmployee());
        conflictingBooking.setTreatment(booking.getTreatment());
        conflictingBooking.setBookingDate(booking.getBookingDate());
        conflictingBooking.setStartTime(booking.getStartTime());
        conflictingBooking.setEndTime(booking.getEndTime());
        conflictingBooking.setStatus(status);
        conflictingBooking.setSlotLocked(false);
        return conflictingBooking;
    }

    private EmployeeEntity buildActiveBookableEmployee(UUID employeeId) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(employeeId);
        employee.setActive(true);
        employee.setBookable(true);
        return employee;
    }

    private BookingRequestDto buildRequest(UUID employeeId, UUID treatmentId) {
        return BookingRequestDto.builder()
                .employeeId(employeeId)
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

    private BookingHoldRequestDto buildHoldRequest(UUID employeeId, UUID treatmentId) {
        return BookingHoldRequestDto.builder()
                .employeeId(employeeId)
                .treatmentId(treatmentId)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .build();
    }
}

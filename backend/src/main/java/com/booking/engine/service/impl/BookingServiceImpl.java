package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingAuditService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingTransactionalOperations;
import com.booking.engine.service.BookingValidator;
import com.booking.engine.service.BookingService;
import com.booking.engine.service.StripePaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BookingService}.
 * Provides booking related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {
    // ---------------------- Constants ----------------------

    private static final int SLOT_HOLD_MINUTES = 10;
    private static final int ADMIN_SLOT_HOLD_MINUTES = 2;
    private static final String ADMIN_HOLD_INVALID_MESSAGE = "This admin-held slot is no longer available. Please choose the time again.";

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    private final EmployeeRepository employeeRepository;

    private final TreatmentRepository treatmentRepository;

    // ---------------------- Mappers ----------------------

    private final BookingMapper mapper;

    // ---------------------- Services ----------------------

    private final AvailabilityService availabilityService;

    private final BookingBlacklistService bookingBlacklistService;

    private final StripePaymentService stripePaymentService;

    private final BookingValidator bookingValidator;

    private final BookingStateMachine bookingStateMachine;

    private final BookingTransactionalOperations bookingTransactionalOperations;

    private final BookingAuditService bookingAuditService;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;
    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public BookingResponseDto getBookingById(UUID id) {
        log.debug("event=booking_get action=start bookingId={}", id);

        BookingEntity booking = findBookingOrThrow(id);

        log.debug("event=booking_get action=success bookingId={}", id);
        return mapper.toDto(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public BookingResponseDto create(BookingRequestDto request) {
        log.info("event=booking_create action=start employeeId={} treatmentId={} bookingDate={} startTime={}",
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());

        SlotHoldEntity reservedSlotHold = bookingTransactionalOperations.reserveDirectBookingSlot(request);
        String paymentIntentId = null;

        try {
            paymentIntentId = stripePaymentService.createAndConfirmPayment(
                    reservedSlotHold.getHoldAmount(),
                    reservedSlotHold.getCustomerEmail(),
                    request.getPaymentMethodId(),
                    buildStripeMetadata(reservedSlotHold));

            bookingTransactionalOperations.attachStripePaymentToSlotHold(
                    reservedSlotHold.getId(),
                    paymentIntentId,
                    "succeeded");

            BookingEntity savedBooking = bookingTransactionalOperations.finalizeDirectBookingFromSlotHold(
                    reservedSlotHold.getId(),
                    "succeeded",
                    "direct create");

            log.info("event=booking_create action=success bookingId={} paymentIntentHash={}",
                    savedBooking.getId(), hashPaymentIntentForLogs(paymentIntentId));

            return mapper.toDto(savedBooking);
        } catch (RuntimeException exception) {
            if (paymentIntentId == null) {
                bookingTransactionalOperations.releaseSlotHold(reservedSlotHold.getId());
            } else {
                log.error(
                        "event=booking_create outcome=persistence_failed_after_payment slotHoldId={} paymentIntentHash={}",
                        reservedSlotHold.getId(),
                        hashPaymentIntentForLogs(paymentIntentId),
                        exception);
            }
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto createAdminBooking(AdminBookingCreateRequestDto request) {
        return createAdminBooking(request, null);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto createAdminBooking(
            AdminBookingCreateRequestDto request,
            String adminHoldSessionId) {
        log.info(
                "event=booking_create_admin action=start employeeId={} treatmentId={} bookingDate={} startTime={}",
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());

        String customerEmail = normalizeOptionalText(request.getCustomerEmail());
        BookingEntity savedBooking;

        if (request.getHoldBookingId() != null) {
            savedBooking = confirmAdminHeldBooking(request, adminHoldSessionId, customerEmail);
        } else {
            EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
            availabilityService.validateSlotSelection(
                    request.getEmployeeId(),
                    request.getTreatmentId(),
                    request.getBookingDate(),
                    request.getStartTime(),
                    request.getEndTime());
            bookingBlacklistService.validateAllowedCustomer(customerEmail, request.getCustomerPhone());

            TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
            BookingEntity booking = buildAdminBooking(request, employee, treatment, customerEmail);
            savedBooking = bookingRepository.save(booking);
        }

        log.info("event=booking_create_admin action=success bookingId={}", savedBooking.getId());
        auditAdminBookingCreated(savedBooking);
        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public BookingResponseDto holdSlot(BookingHoldRequestDto request, String clientIp, String clientDeviceId) {
        log.info("event=booking_hold_public action=start employeeId={} treatmentId={} bookingDate={} startTime={}",
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());

        validateHoldLimit(clientIp, clientDeviceId);

        EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
        availabilityService.validateSlotSelection(
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        BigDecimal paymentAmount = treatment.getPrice();

        SlotHoldEntity slotHold = buildSlotHold(
                request,
                employee,
                treatment,
                paymentAmount,
                SlotHoldScope.PUBLIC,
                clientIp,
                clientDeviceId,
                LocalDateTime.now(getZoneId()).plusMinutes(SLOT_HOLD_MINUTES));
        SlotHoldEntity savedSlotHold = slotHoldRepository.save(slotHold);

        log.info("event=booking_hold_public action=success slotHoldId={} expiresAt={}",
                savedSlotHold.getId(), savedSlotHold.getExpiresAt());

        return toHoldResponseDto(savedSlotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public BookingResponseDto holdAdminSlot(BookingHoldRequestDto request, String adminHoldSessionId) {
        log.info("event=booking_hold_admin action=start employeeId={} treatmentId={} bookingDate={} startTime={}",
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());

        validateAdminHoldSessionId(adminHoldSessionId);
        releaseExistingAdminSessionHolds(adminHoldSessionId, null);

        EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
        availabilityService.validateSlotSelection(
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        SlotHoldEntity slotHold = buildSlotHold(
                request,
                employee,
                treatment,
                treatment.getPrice(),
                SlotHoldScope.ADMIN,
                BookingStateMachine.ADMIN_HOLD_CLIENT_IP,
                toAdminHoldDeviceId(adminHoldSessionId),
                LocalDateTime.now(getZoneId()).plusMinutes(ADMIN_SLOT_HOLD_MINUTES));
        SlotHoldEntity savedSlotHold = slotHoldRepository.save(slotHold);

        log.info("event=booking_hold_admin action=success slotHoldId={} expiresAt={}",
                savedSlotHold.getId(), savedSlotHold.getExpiresAt());
        return toHoldResponseDto(savedSlotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto refreshAdminHold(UUID id, String adminHoldSessionId) {
        log.info("event=booking_hold_admin_refresh action=start bookingId={}", id);

        validateAdminHoldSessionId(adminHoldSessionId);
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            validateAdminSlotHoldOwnership(slotHold, adminHoldSessionId);
            validateAdminSlotHoldAvailability(slotHold);

            slotHold.setExpiresAt(LocalDateTime.now(getZoneId()).plusMinutes(ADMIN_SLOT_HOLD_MINUTES));
            SlotHoldEntity savedSlotHold = slotHoldRepository.save(slotHold);
            log.info("event=booking_hold_admin_refresh action=success entityType=slot_hold slotHoldId={} expiresAt={}",
                    savedSlotHold.getId(),
                    savedSlotHold.getExpiresAt());
            return toHoldResponseDto(savedSlotHold);
        }

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        validateAdminHoldOwnership(booking, adminHoldSessionId);
        validateAdminHoldAvailability(booking);

        booking.setExpiresAt(LocalDateTime.now(getZoneId()).plusMinutes(ADMIN_SLOT_HOLD_MINUTES));
        BookingEntity savedBooking = bookingRepository.save(booking);
        log.info("event=booking_hold_admin_refresh action=success entityType=booking bookingId={} expiresAt={}",
                savedBooking.getId(),
                savedBooking.getExpiresAt());

        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void releaseAdminHold(UUID id, String adminHoldSessionId) {
        log.info("event=booking_hold_admin_release action=start bookingId={}", id);

        validateAdminHoldSessionId(adminHoldSessionId);
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            if (!matchesAdminHoldSession(slotHold, adminHoldSessionId)) {
                log.warn("event=booking_hold_admin_release outcome=ignored_session_mismatch slotHoldId={}", id);
                return;
            }

            releaseSlotHoldInternal(slotHold);
            log.info("event=booking_hold_admin_release action=success entityType=slot_hold slotHoldId={}", id);
            return;
        }

        BookingEntity booking = bookingRepository.findByIdForUpdate(id).orElse(null);

        if (booking == null) {
            log.debug("event=booking_hold_admin_release outcome=already_released bookingId={}", id);
            return;
        }

        if (!matchesAdminHoldSession(booking, adminHoldSessionId)) {
            log.warn("event=booking_hold_admin_release outcome=ignored_session_mismatch bookingId={}", id);
            return;
        }

        releaseAdminHoldInternal(booking);
        log.info("event=booking_hold_admin_release action=success entityType=booking bookingId={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void validateHeldBookingCheckout(UUID id, BookingCheckoutValidationRequestDto request) {
        log.debug("event=booking_checkout_validate action=start bookingId={} customerEmailMask={}",
                id,
                maskCustomerEmailForLogs(request.getCustomer() != null ? request.getCustomer().getEmail() : null));

        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            validatePublicSlotHoldAvailability(slotHold);
            validatePublicCustomerAllowed(
                    request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                    request.getCustomer() != null ? request.getCustomer().getPhone() : null);
            return;
        }

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        validatePendingBookingAvailability(booking);
        validatePublicCustomerAllowed(
                request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                request.getCustomer() != null ? request.getCustomer().getPhone() : null);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public BookingCheckoutSessionResponseDto prepareHeldBookingCheckout(
            UUID id,
            BookingCheckoutSessionRequestDto request) {
        log.info(
                "event=booking_checkout_prepare action=start bookingId={} confirmationTokenPresent={} customerEmailMask={}",
                id,
                request.getConfirmationTokenId() != null && !request.getConfirmationTokenId().isBlank(),
                maskCustomerEmailForLogs(request.getCustomer() != null ? request.getCustomer().getEmail() : null));

        BookingTransactionalOperations.CheckoutPreparationTarget target = bookingTransactionalOperations
                .prepareCheckoutTarget(id, request);

        if (target.existingPaymentIntentId() != null && !target.existingPaymentIntentId().isBlank()) {
            String currentStatus = stripePaymentService.getPaymentIntentStatus(target.existingPaymentIntentId());
            boolean persistNonSuccessStatus = target.targetType() == BookingTransactionalOperations.CheckoutTargetType.SLOT_HOLD;
            bookingTransactionalOperations.persistCheckoutResult(
                    target,
                    target.existingPaymentIntentId(),
                    currentStatus,
                    persistNonSuccessStatus);

            log.info("event=booking_checkout_prepare action=reuse_payment_intent targetType={} targetId={} paymentIntentHash={} paymentStatus={}",
                    target.targetType(),
                    target.targetId(),
                    hashPaymentIntentForLogs(target.existingPaymentIntentId()),
                    currentStatus);

            if ("succeeded".equals(currentStatus)) {
                return BookingCheckoutSessionResponseDto.builder()
                        .paymentIntentId(target.existingPaymentIntentId())
                        .paymentStatus(currentStatus)
                        .build();
            }

            if ("requires_action".equals(currentStatus) || "processing".equals(currentStatus)) {
                throw new BookingValidationException(
                        "Stripe is still processing the payment for this booking. Please finish the current checkout flow.");
            }
        }

        log.info("event=booking_checkout_prepare action=create_payment_intent targetType={} targetId={} amount={}",
                target.targetType(),
                target.targetId(),
                target.holdAmount());
        BookingCheckoutSessionResponseDto checkoutSession = stripePaymentService
                .createAndConfirmPaymentWithConfirmationToken(
                        target.holdAmount(),
                        target.customerEmail(),
                        request.getConfirmationTokenId(),
                        target.stripeMetadata());

        boolean persistNonSuccessStatus = true;
        bookingTransactionalOperations.persistCheckoutResult(
                target,
                checkoutSession.getPaymentIntentId(),
                checkoutSession.getPaymentStatus(),
                persistNonSuccessStatus);

        log.info("event=booking_checkout_prepare action=success targetType={} targetId={} paymentIntentHash={} paymentStatus={} hasClientSecret={}",
                target.targetType(),
                target.targetId(),
                hashPaymentIntentForLogs(checkoutSession.getPaymentIntentId()),
                checkoutSession.getPaymentStatus(),
                checkoutSession.getClientSecret() != null && !checkoutSession.getClientSecret().isBlank());

        return checkoutSession;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public BookingResponseDto confirmHeldBooking(UUID id, BookingConfirmationRequestDto request) {
        log.info("event=booking_confirm action=start bookingId={}", id);
        String paymentStatus = stripePaymentService.getPaymentIntentStatus(request.getPaymentIntentId());
        BookingEntity confirmedBooking = bookingTransactionalOperations.confirmHeldBookingAfterPaymentStatus(
                id,
                request,
                paymentStatus);
        return mapper.toDto(confirmedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void cancelBooking(UUID id) {
        log.info("event=booking_cancel_public action=start bookingId={}", id);

        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            validatePublicSlotHoldCancellation(slotHold);
            releaseSlotHoldInternal(slotHold);
            log.info("event=booking_cancel_public action=success slotHoldId={}", id);
            return;
        }

        BookingEntity booking = findBookingOrThrow(id);
        validatePublicCancellation(booking, id);

        cancelPublicBooking(booking);
        bookingRepository.save(booking);

        log.info("event=booking_cancel_public action=success bookingId={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminBookingListResponseDto getAdminBookings(String search) {
        log.debug("event=booking_admin_list action=start searchHash={}", hashSearchForLogs(search));

        List<BookingEntity> activeBookings = bookingRepository.findAllActiveWithEmployeeAndTreatment();
        long confirmedCount = activeBookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .count();

        List<BookingResponseDto> filteredBookings = activeBookings.stream()
                .sorted(buildAdminBookingComparator())
                .filter(booking -> matchesAdminSearch(booking, search))
                .map(mapper::toDto)
                .toList();

        AdminBookingListResponseDto response = AdminBookingListResponseDto.builder()
                .bookings(filteredBookings)
                .confirmedCount(confirmedCount)
                .filteredCount(filteredBookings.size())
                .build();
        log.debug("event=booking_admin_list action=success totalCount={} filteredCount={} confirmedCount={}",
                activeBookings.size(),
                response.getFilteredCount(),
                response.getConfirmedCount());
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AdminBookingCustomerLookupResponseDto> findLatestCustomerByPhone(String phone) {
        String normalizedPhone = normalizePhoneSearch(phone);
        if (normalizedPhone == null) {
            return Optional.empty();
        }

        return bookingRepository.findAllByActiveTrueAndCustomerPhoneIsNotNullOrderByCreatedAtDesc()
                .stream()
                .filter(booking -> normalizedPhone.equals(normalizePhoneSearch(booking.getCustomerPhone())))
                .findFirst()
                .map(booking -> AdminBookingCustomerLookupResponseDto.builder()
                        .customerName(booking.getCustomerName())
                        .customerPhone(booking.getCustomerPhone())
                        .customerEmail(booking.getCustomerEmail())
                        .build());
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto updateBookingByAdmin(UUID id, AdminBookingUpdateRequestDto request) {
        log.info(
                "event=booking_update_admin action=start bookingId={} employeeId={} treatmentId={} bookingDate={} startTime={} status={}",
                id,
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getStatus());

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        BookingStatus previousStatus = booking.getStatus();
        EmployeeEntity employee = findActiveEmployeeForAdminUpdate(request.getEmployeeId());
        TreatmentEntity treatment = findActiveTreatmentOrThrow(request.getTreatmentId());

        if (slotDefinitionChanged(booking, request) && shouldValidateAdminBookingSlot(request.getStatus())) {
            availabilityService.validateSlotSelectionExcludingBooking(
                    request.getEmployeeId(),
                    request.getTreatmentId(),
                    request.getBookingDate(),
                    request.getStartTime(),
                    request.getEndTime(),
                    booking.getId());
        }

        bookingBlacklistService.validateAllowedCustomer(
                normalizeOptionalText(request.getCustomerEmail()),
                normalizeOptionalText(request.getCustomerPhone()));

        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setCustomerName(request.getCustomerName().trim());
        booking.setCustomerPhone(normalizeOptionalText(request.getCustomerPhone()));
        booking.setCustomerEmail(normalizeOptionalText(request.getCustomerEmail()));
        booking.setHoldAmount(request.getHoldAmount());
        applyAdminUpdateState(booking, request.getStatus());

        BookingEntity savedBooking = bookingRepository.save(booking);
        log.info("event=booking_update_admin action=success bookingId={} status={}",
                savedBooking.getId(),
                savedBooking.getStatus());
        auditAdminBookingUpdated(savedBooking, previousStatus);
        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto cancelBookingByAdmin(UUID id) {
        log.info("event=booking_cancel_admin action=start bookingId={}", id);

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        BookingStatus previousStatus = booking.getStatus();
        validateAdminCancellation(booking);

        cancelByAdmin(booking);

        BookingEntity savedBooking = bookingRepository.save(booking);
        log.info("event=booking_cancel_admin action=success bookingId={}", id);
        auditAdminBookingCancelled(savedBooking, previousStatus);

        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void syncStripePaymentIntentFromWebhook(String paymentIntentId, String paymentStatus, String eventType) {
        syncStripePaymentIntentFromWebhook(paymentIntentId, paymentStatus, eventType, Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void syncStripePaymentIntentFromWebhook(
            String paymentIntentId,
            String paymentStatus,
            String eventType,
            Map<String, String> metadata) {
        log.info("event=booking_webhook_sync action=start paymentIntentHash={} eventType={} paymentStatus={}",
                hashPaymentIntentForLogs(paymentIntentId), eventType, paymentStatus);

        BookingEntity booking = bookingRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId).orElse(null);
        if (booking == null) {
            booking = findBookingByWebhookMetadata(metadata, paymentIntentId);
        }
        if (booking != null) {
            booking = attachPaymentIntentIfMissing(booking, paymentIntentId);
            if (booking == null) {
                return;
            }

            if ("payment_intent.succeeded".equals(eventType)) {
                applySuccessfulStripePaymentState(booking, paymentStatus, "webhook");
            } else if ("payment_intent.canceled".equals(eventType)
                    || "payment_intent.payment_failed".equals(eventType)) {
                applyFailedStripePaymentState(booking, paymentStatus);
            }

            bookingRepository.save(booking);
            log.info("event=booking_webhook_sync action=success entityType=booking bookingId={} eventType={} paymentStatus={} status={}",
                    booking.getId(), eventType, paymentStatus, booking.getStatus());
            return;
        }

        SlotHoldEntity slotHold = slotHoldRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId).orElse(null);
        if (slotHold == null) {
            slotHold = findSlotHoldByWebhookMetadata(metadata, paymentIntentId);
        }
        if (slotHold != null) {
            slotHold = attachPaymentIntentIfMissing(slotHold, paymentIntentId);
            if (slotHold == null) {
                return;
            }

            if ("payment_intent.succeeded".equals(eventType)) {
                finalizePaidSlotHold(slotHold, paymentStatus, "webhook");
            } else if ("payment_intent.canceled".equals(eventType)
                    || "payment_intent.payment_failed".equals(eventType)) {
                applyFailedStripePaymentState(slotHold, paymentStatus);
                slotHoldRepository.save(slotHold);
            }

            log.info("event=booking_webhook_sync action=success entityType=slot_hold slotHoldId={} eventType={} paymentStatus={}",
                    slotHold.getId(), eventType, paymentStatus);
            return;
        }

        log.warn("event=booking_webhook_sync outcome=not_found paymentIntentHash={}",
                hashPaymentIntentForLogs(paymentIntentId));
    }

    // ---------------------- Private Methods ----------------------

    /**
     * Finds an active bookable employee with a write lock or throws when it cannot be booked.
     */
    private EmployeeEntity findActiveEmployeeForUpdate(UUID employeeId) {
        EmployeeEntity employee = employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)
                .orElseThrow(() -> {
                    log.warn("event=employee_lookup outcome=not_found employeeId={}", employeeId);
                    return new EntityNotFoundException("Employee", employeeId);
                });

        if (!Boolean.TRUE.equals(employee.getBookable())) {
            log.warn("event=employee_lookup outcome=not_bookable employeeId={}", employeeId);
            throw new BookingValidationException("Employee is not available for booking");
        }

        return employee;
    }

    /**
     * Finds booking by webhook metadata.
     */
    private BookingEntity findBookingByWebhookMetadata(Map<String, String> metadata, String paymentIntentId) {
        UUID bookingId = parseWebhookMetadataUuid(metadata, "bookingId", paymentIntentId);
        if (bookingId == null) {
            return null;
        }

        BookingEntity booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking != null) {
            log.info("event=booking_webhook_sync action=recover_context entityType=booking bookingId={} paymentIntentHash={}",
                    bookingId,
                    hashPaymentIntentForLogs(paymentIntentId));
        }
        return booking;
    }

    /**
     * Finds slot hold by webhook metadata.
     */
    private SlotHoldEntity findSlotHoldByWebhookMetadata(Map<String, String> metadata, String paymentIntentId) {
        UUID slotHoldId = parseWebhookMetadataUuid(metadata, "slotHoldId", paymentIntentId);
        if (slotHoldId == null) {
            return null;
        }

        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(slotHoldId).orElse(null);
        if (slotHold != null) {
            log.info("event=booking_webhook_sync action=recover_context entityType=slot_hold slotHoldId={} paymentIntentHash={}",
                    slotHoldId,
                    hashPaymentIntentForLogs(paymentIntentId));
        }
        return slotHold;
    }

    /**
     * Handles parse webhook metadata uuid.
     */
    private UUID parseWebhookMetadataUuid(Map<String, String> metadata, String key, String paymentIntentId) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }

        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            log.warn("event=booking_webhook_sync outcome=ignored_invalid_metadata key={} paymentIntentHash={}",
                    key,
                    hashPaymentIntentForLogs(paymentIntentId));
            return null;
        }
    }

    /**
     * Handles attach payment intent if missing.
     */
    private BookingEntity attachPaymentIntentIfMissing(BookingEntity booking, String paymentIntentId) {
        if (booking.getStripePaymentIntentId() == null || booking.getStripePaymentIntentId().isBlank()) {
            booking.setStripePaymentIntentId(paymentIntentId);
            return booking;
        }

        if (!paymentIntentId.equals(booking.getStripePaymentIntentId())) {
            log.warn("event=booking_webhook_sync outcome=ignored_payment_intent_mismatch entityType=booking paymentIntentHash={} bookingId={} existingPaymentIntentHash={}",
                    hashPaymentIntentForLogs(paymentIntentId),
                    booking.getId(),
                    hashPaymentIntentForLogs(booking.getStripePaymentIntentId()));
            return null;
        }

        return booking;
    }

    /**
     * Handles attach payment intent if missing.
     */
    private SlotHoldEntity attachPaymentIntentIfMissing(SlotHoldEntity slotHold, String paymentIntentId) {
        if (slotHold.getStripePaymentIntentId() == null || slotHold.getStripePaymentIntentId().isBlank()) {
            slotHold.setStripePaymentIntentId(paymentIntentId);
            return slotHold;
        }

        if (!paymentIntentId.equals(slotHold.getStripePaymentIntentId())) {
            log.warn("event=booking_webhook_sync outcome=ignored_payment_intent_mismatch entityType=slot_hold paymentIntentHash={} slotHoldId={} existingPaymentIntentHash={}",
                    hashPaymentIntentForLogs(paymentIntentId),
                    slotHold.getId(),
                    hashPaymentIntentForLogs(slotHold.getStripePaymentIntentId()));
            return null;
        }

        return slotHold;
    }

    /*
     * Finds an active employee for admin booking edits without requiring the
     * public-bookable flag, so existing bookings can still be maintained even
     * after team availability settings change.
     *
     * @param employeeId employee UUID
     * 
     * @return locked employee entity
     */
    private EmployeeEntity findActiveEmployeeForAdminUpdate(UUID employeeId) {
        return employeeRepository.findByIdAndActiveTrueForUpdate(employeeId)
                .orElseThrow(() -> {
                    log.warn("event=employee_lookup outcome=not_found employeeId={}", employeeId);
                    return new EntityNotFoundException("Employee", employeeId);
                });
    }

    /*
     * Finds treatment by ID or throws exception.
     *
     * @param treatmentId the treatment UUID
     * 
     * @return the treatment entity
     * 
     * @throws EntityNotFoundException if not found
     */
    private TreatmentEntity findTreatmentOrThrow(UUID treatmentId) {
        return treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup outcome=not_found treatmentId={}", treatmentId);
                    return new EntityNotFoundException("Treatment", treatmentId);
                });
    }

    /*
     * Finds an active treatment for admin booking edits.
     *
     * @param treatmentId treatment UUID
     * 
     * @return active treatment entity
     */
    private TreatmentEntity findActiveTreatmentOrThrow(UUID treatmentId) {
        return treatmentRepository.findByIdAndActiveTrue(treatmentId)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup outcome=not_found_active treatmentId={}", treatmentId);
                    return new EntityNotFoundException("Treatment", treatmentId);
                });
    }

    /*
     * Finds booking by ID or throws exception.
     *
     * @param id the booking UUID
     * 
     * @return the booking entity
     * 
     * @throws EntityNotFoundException if not found
     */
    private BookingEntity findBookingOrThrow(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("event=booking_lookup outcome=not_found bookingId={}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /*
     * Finds booking by ID with pessimistic write lock or throws exception.
     *
     * @param id the booking UUID
     * 
     * @return the locked booking entity
     */
    private BookingEntity findBookingForUpdateOrThrow(UUID id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("event=booking_lookup outcome=not_found_for_update bookingId={}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /*
     * Validates Stripe payment intent is present for booking.
     */
    private void validateStripeIntentExists(BookingEntity booking) {
        if (booking.getStripePaymentIntentId() == null || booking.getStripePaymentIntentId().isBlank()) {
            throw new IllegalStateException("Booking has no Stripe payment intent");
        }
    }

    /*
     * Validates a temporary slot hold already has an associated Stripe
     * PaymentIntent.
     */
    private void validateStripeIntentExists(SlotHoldEntity slotHold) {
        if (slotHold.getStripePaymentIntentId() == null || slotHold.getStripePaymentIntentId().isBlank()) {
            throw new IllegalStateException("Slot hold has no Stripe payment intent");
        }
    }

    /*
     * Ensures a held booking is still eligible for final payment confirmation by
     * rejecting cancelled, expired, completed, or stale pending holds and marking
     * truly expired holds in persistence before failing.
     *
     * @param booking booking being finalized after checkout
     */
    private void validateBookingCanFinalizePayment(BookingEntity booking) {
        bookingValidator.validateBookingCanFinalizePayment(booking);
    }

    /*
     * Prevents successful-payment reconciliation from reviving bookings that were
     * explicitly cancelled or already completed.
     *
     * @param booking booking being synchronized after Stripe reported success
     */
    private void validateBookingCanAcceptSuccessfulPayment(BookingEntity booking) {
        bookingValidator.validateBookingCanAcceptSuccessfulPayment(booking);
    }

    /*
     * Validates a pending booking can still be processed before the salon decision.
     */
    private void validatePendingBookingAvailability(BookingEntity booking) {
        bookingValidator.validatePendingBookingAvailability(booking);
    }

    /*
     * Public checkout may only use public-facing slot holds.
     */
    private void validatePublicSlotHoldAvailability(SlotHoldEntity slotHold) {
        bookingValidator.validatePublicSlotHoldAvailability(slotHold);
    }

    /*
     * Public cancellation may only release unpaid public slot holds.
     */
    private void validatePublicSlotHoldCancellation(SlotHoldEntity slotHold) {
        bookingValidator.validatePublicSlotHoldCancellation(slotHold);
    }

    /*
     * Prevents finalize calls from continuing when the temporary slot hold is no
     * longer eligible for payment completion.
     */
    private void validateSlotHoldCanFinalizePayment(SlotHoldEntity slotHold) {
        bookingValidator.validateSlotHoldCanFinalizePayment(slotHold);
    }

    /*
     * Successful payment may still finalize an otherwise expired slot hold, but
     * only while the slot itself remains free.
     */
    private void validateSlotHoldCanAcceptSuccessfulPayment(SlotHoldEntity slotHold) {
        bookingValidator.validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
    }

    /*
     * Validates public cancellation is used only for unpaid temporary holds.
     */
    private void validatePublicCancellation(BookingEntity booking, UUID id) {
        bookingValidator.validatePublicCancellation(booking, id);
    }

    /*
     * Validates admin-side cancellation request.
     */
    private void validateAdminCancellation(BookingEntity booking) {
        bookingValidator.validateAdminCancellation(booking);
    }

    /*
     * Finalizes a previously held admin-panel slot into a confirmed phone booking.
     */
    private BookingEntity confirmAdminHeldBooking(
            AdminBookingCreateRequestDto request,
            String adminHoldSessionId,
            String customerEmail) {
        validateAdminHoldSessionId(adminHoldSessionId);

        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(request.getHoldBookingId()).orElse(null);
        if (slotHold != null) {
            validateAdminSlotHoldOwnership(slotHold, adminHoldSessionId);
            validateAdminSlotHoldAvailability(slotHold);

            if (!matchesAdminHeldSlot(slotHold, request)) {
                throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
            }

            EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
            bookingBlacklistService.validateAllowedCustomer(customerEmail, request.getCustomerPhone());
            TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());

            BookingEntity booking = new BookingEntity();
            applyConfirmedAdminBookingDetails(booking, request, employee, treatment, customerEmail);
            BookingEntity savedBooking = bookingRepository.save(booking);
            releaseSlotHoldInternal(slotHold);
            return savedBooking;
        }

        BookingEntity booking = findBookingForUpdateOrThrow(request.getHoldBookingId());
        validateAdminHoldOwnership(booking, adminHoldSessionId);
        validateAdminHoldAvailability(booking);

        if (!matchesAdminHeldSlot(booking, request)) {
            throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
        }

        EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
        availabilityService.validateSlotSelectionExcludingBooking(
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime(),
                booking.getId());
        bookingBlacklistService.validateAllowedCustomer(customerEmail, request.getCustomerPhone());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        applyConfirmedAdminBookingDetails(booking, request, employee, treatment, customerEmail);
        return bookingRepository.save(booking);
    }

    /*
     * Determines whether an admin edit changed the slot-defining fields and
     * therefore needs a fresh availability validation.
     *
     * @param booking current persisted booking
     * 
     * @param request admin update payload
     * 
     * @return {@code true} when employee, treatment, date, or time changed
     */
    private boolean slotDefinitionChanged(BookingEntity booking, AdminBookingUpdateRequestDto request) {
        return bookingValidator.slotDefinitionChanged(booking, request);
    }

    /*
     * Determines whether the admin-edited booking status should still reserve
     * the slot on the public website.
     *
     * @param status requested booking status
     * 
     * @return {@code true} when slot validation should run for moved bookings
     */
    private boolean shouldValidateAdminBookingSlot(BookingStatus status) {
        return bookingValidator.shouldValidateAdminBookingSlot(status);
    }

    /*
     * Normalizes optional free-text values before persisting them.
     *
     * @param value raw value
     * 
     * @return trimmed value or {@code null} when blank
     */
    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    /*
     * Applies the canonical state transition for a Stripe PaymentIntent that has
     * already succeeded, keeping webhook and direct confirmation flows
     * idempotent and aligned.
     *
     * @param booking booking being updated
     * 
     * @param paymentStatus Stripe PaymentIntent status
     * 
     * @param source short source label for diagnostics
     */
    private void applySuccessfulStripePaymentState(BookingEntity booking, String paymentStatus, String source) {
        bookingStateMachine.applySuccessfulStripePaymentState(booking, paymentStatus, source);
    }

    /**
     * Applies failed stripe payment state.
     */
    private void applyFailedStripePaymentState(BookingEntity booking, String paymentStatus) {
        bookingStateMachine.applyFailedStripePaymentState(booking, paymentStatus);
    }

    /**
     * Applies failed stripe payment state.
     */
    private void applyFailedStripePaymentState(SlotHoldEntity slotHold, String paymentStatus) {
        bookingStateMachine.applyFailedStripePaymentState(slotHold, paymentStatus);
    }

    /**
     * Cancels public booking.
     */
    private void cancelPublicBooking(BookingEntity booking) {
        bookingStateMachine.cancelPublicBooking(booking);
    }

    /**
     * Cancels by admin.
     */
    private void cancelByAdmin(BookingEntity booking) {
        bookingStateMachine.cancelByAdmin(booking);
    }

    /**
     * Applies admin update state.
     */
    private void applyAdminUpdateState(BookingEntity booking, BookingStatus status) {
        bookingStateMachine.applyAdminUpdateState(booking, status);
    }

    /*
     * Builds the admin booking sort order that keeps upcoming bookings first and
     * pushes historical bookings to the bottom while preserving sensible date and
     * time ordering inside each group.
     *
     * @return comparator for admin booking list rendering
     */
    private Comparator<BookingEntity> buildAdminBookingComparator() {
        LocalDate today = LocalDate.now(getZoneId());
        LocalTime nowTime = LocalTime.now(getZoneId());

        return (left, right) -> {
            boolean leftPast = isPastBooking(left, today, nowTime);
            boolean rightPast = isPastBooking(right, today, nowTime);

            if (leftPast != rightPast) {
                return leftPast ? 1 : -1;
            }

            int dateComparison = leftPast
                    ? right.getBookingDate().compareTo(left.getBookingDate())
                    : left.getBookingDate().compareTo(right.getBookingDate());

            if (dateComparison != 0) {
                return dateComparison;
            }

            int timeComparison = leftPast
                    ? right.getStartTime().compareTo(left.getStartTime())
                    : left.getStartTime().compareTo(right.getStartTime());

            if (timeComparison != 0) {
                return timeComparison;
            }

            LocalDateTime leftCreatedAt = left.getCreatedAt();
            LocalDateTime rightCreatedAt = right.getCreatedAt();

            if (leftCreatedAt == null && rightCreatedAt == null) {
                return 0;
            }

            if (leftCreatedAt == null) {
                return 1;
            }

            if (rightCreatedAt == null) {
                return -1;
            }

            return leftCreatedAt.compareTo(rightCreatedAt);
        };
    }

    /*
     * Determines whether a booking belongs to the past relative to the current
     * business date and time, using end time for same-day bookings.
     *
     * @param booking booking to inspect
     * 
     * @param today current date
     * 
     * @param nowTime current time
     * 
     * @return {@code true} when the booking is already past
     */
    private boolean isPastBooking(BookingEntity booking, LocalDate today, LocalTime nowTime) {
        return booking.getBookingDate().isBefore(today)
                || (booking.getBookingDate().isEqual(today) && booking.getEndTime() != null
                        && booking.getEndTime().isBefore(nowTime));
    }

    /*
     * Applies normalized text and digit-only phone matching so admin search can
     * find bookings by customer name, email, raw phone text, or phone digits.
     *
     * @param booking booking to inspect
     * 
     * @param search raw admin search string
     * 
     * @return {@code true} when the booking matches the search query
     */
    private boolean matchesAdminSearch(BookingEntity booking, String search) {
        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch == null) {
            return true;
        }

        String normalizedPhoneSearch = normalizePhoneSearch(search);

        return containsIgnoreCase(booking.getCustomerName(), normalizedSearch)
                || containsIgnoreCase(booking.getCustomerEmail(), normalizedSearch)
                || containsIgnoreCase(booking.getCustomerPhone(), normalizedSearch)
                || (normalizedPhoneSearch != null && normalizePhoneSearch(booking.getCustomerPhone()) != null
                        && normalizePhoneSearch(booking.getCustomerPhone()).contains(normalizedPhoneSearch));
    }

    /*
     * Normalizes free-text admin search input by trimming, lowercasing, and
     * converting blank input to {@code null}.
     *
     * @param value raw search value
     * 
     * @return normalized search text or {@code null} when blank
     */
    private String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim().toLowerCase();
        return trimmed.isBlank() ? null : trimmed;
    }

    /*
     * Extracts digits from a phone-like search value so formatted and unformatted
     * phone numbers can be compared consistently.
     *
     * @param value raw phone search value
     * 
     * @return normalized digit-only string or {@code null} when blank
     */
    private String normalizePhoneSearch(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }

    /*
     * Performs a null-safe case-insensitive containment check used by the admin
     * search filter.
     *
     * @param value source text
     * 
     * @param search normalized search text
     * 
     * @return {@code true} when {@code value} contains {@code search}
     */
    private boolean containsIgnoreCase(String value, String search) {
        return value != null && search != null && value.toLowerCase().contains(search);
    }

    /*
     * Rejects public bookings from customers whose normalized email or phone is
     * present in the booking blacklist.
     *
     * @param email customer email
     * 
     * @param phone customer phone
     */
    private void validatePublicCustomerAllowed(String email, String phone) {
        bookingValidator.validatePublicCustomerAllowed(email, phone);
    }

    /*
     * Enforces per-IP and per-device limits for unpaid active slot holds to reduce
     * abuse of temporary reservations.
     *
     * @param clientIp resolved client IP address
     * 
     * @param clientDeviceId persistent client device identifier
     */
    private void validateHoldLimit(String clientIp, String clientDeviceId) {
        bookingValidator.validateHoldLimit(clientIp, clientDeviceId);
    }

    /*
     * Ensures a non-empty admin hold session id is present before hold endpoints
     * are used.
     */
    private void validateAdminHoldSessionId(String adminHoldSessionId) {
        bookingValidator.validateAdminHoldSessionId(adminHoldSessionId);
    }

    /*
     * Releases any older pending admin holds for the same admin session so only
     * the latest selected slot remains reserved.
     */
    private void releaseExistingAdminSessionHolds(String adminHoldSessionId, UUID keepHoldId) {
        LocalDateTime now = LocalDateTime.now(getZoneId());
        String holdDeviceId = toAdminHoldDeviceId(adminHoldSessionId);

        List<SlotHoldEntity> existingSlotHolds = Optional.ofNullable(
                slotHoldRepository.findActiveByScopeAndHoldClientDeviceId(
                        SlotHoldScope.ADMIN,
                        holdDeviceId,
                        now))
                .orElse(List.of());

        existingSlotHolds.stream()
                .filter(slotHold -> keepHoldId == null || !keepHoldId.equals(slotHold.getId()))
                .forEach(this::releaseSlotHoldInternal);

        List<BookingEntity> existingHolds = Optional.ofNullable(
                bookingRepository.findByHoldClientDeviceIdAndStatus(holdDeviceId, BookingStatus.PENDING))
                .orElse(List.of());

        existingHolds.stream()
                .filter(booking -> keepHoldId == null || !keepHoldId.equals(booking.getId()))
                .forEach(this::releaseAdminHoldInternal);
    }

    /*
     * Detects whether a pending booking still blocks the slot because it is paid
     * already or its temporary hold has not expired yet.
     *
     * @param booking candidate booking from the same employee/day
     * 
     * @param now current timestamp in booking timezone
     * 
     * @return {@code true} when the pending booking must keep the slot blocked
     */
    private boolean isBlockingPendingSlot(BookingEntity booking, LocalDateTime now) {
        return bookingStateMachine.isBlockingPendingSlot(booking, now);
    }

    /*
     * Detects whether an unpaid temporary hold is still active.
     *
     * @param booking pending booking hold
     * 
     * @param now current timestamp in booking timezone
     * 
     * @return {@code true} when the hold still blocks the slot
     */
    private boolean isActiveHoldSlot(BookingEntity booking, LocalDateTime now) {
        return bookingStateMachine.isActiveHoldSlot(booking, now);
    }

    /*
     * Detects an admin-cancelled booking that intentionally keeps the slot locked.
     *
     * @param booking candidate booking from the same employee/day
     * 
     * @return {@code true} when the cancelled booking should still block the slot
     */
    private boolean isLockedCancelledSlot(BookingEntity booking) {
        return bookingStateMachine.isLockedCancelledSlot(booking);
    }

    /*
     * Verifies that the pending booking belongs to the current admin-panel hold
     * session.
     */
    private void validateAdminHoldOwnership(BookingEntity booking, String adminHoldSessionId) {
        bookingValidator.validateAdminHoldOwnership(booking, adminHoldSessionId);
    }

    /*
     * Verifies that the temporary slot hold belongs to the current admin-panel
     * session.
     */
    private void validateAdminSlotHoldOwnership(SlotHoldEntity slotHold, String adminHoldSessionId) {
        bookingValidator.validateAdminSlotHoldOwnership(slotHold, adminHoldSessionId);
    }

    /*
     * Verifies that the admin hold is still the same kind of unpaid pending slot
     * reservation created from the admin booking page.
     */
    private void validateAdminHoldAvailability(BookingEntity booking) {
        bookingValidator.validateAdminHoldAvailability(booking);
    }

    /*
     * Verifies that the admin slot hold is still active and still owns a free
     * slot.
     */
    private void validateAdminSlotHoldAvailability(SlotHoldEntity slotHold) {
        bookingValidator.validateAdminSlotHoldAvailability(slotHold);
    }

    /*
     * Determines whether a booking belongs to the current admin hold session.
     */
    private boolean matchesAdminHoldSession(BookingEntity booking, String adminHoldSessionId) {
        return bookingStateMachine.matchesAdminHoldSession(booking, adminHoldSessionId);
    }

    /*
     * Determines whether a slot hold belongs to the current admin hold session.
     */
    private boolean matchesAdminHoldSession(SlotHoldEntity slotHold, String adminHoldSessionId) {
        return bookingStateMachine.matchesAdminHoldSession(slotHold, adminHoldSessionId);
    }

    /*
     * Detects whether the booking is an unpaid admin-panel hold.
     */
    private boolean isAdminPanelHold(BookingEntity booking) {
        return bookingStateMachine.isAdminPanelHold(booking);
    }

    /*
     * Detects whether the slot hold was created by the admin booking panel.
     */
    private boolean isAdminPanelHold(SlotHoldEntity slotHold) {
        return bookingStateMachine.isAdminPanelHold(slotHold);
    }

    /*
     * Releases an admin-panel hold without affecting already confirmed bookings.
     */
    private void releaseAdminHoldInternal(BookingEntity booking) {
        bookingStateMachine.releaseAdminHold(booking);
    }

    /*
     * Removes a temporary slot hold once it is no longer needed.
     */
    private void releaseSlotHoldInternal(SlotHoldEntity slotHold) {
        bookingStateMachine.releaseSlotHold(slotHold);
    }

    /*
     * Maps an admin hold session id to the booking hold device token stored in the
     * database.
     */
    private String toAdminHoldDeviceId(String adminHoldSessionId) {
        return bookingStateMachine.toAdminHoldDeviceId(adminHoldSessionId);
    }

    /*
     * Builds a non-PII metadata payload for Stripe PaymentIntent.
     */
    private Map<String, String> buildStripeMetadata(BookingRequestDto request) {
        return Map.of(
                "employeeId", request.getEmployeeId().toString(),
                "treatmentId", request.getTreatmentId().toString(),
                "bookingDate", request.getBookingDate().toString(),
                "startTime", request.getStartTime().toString(),
                "endTime", request.getEndTime().toString());
    }

    /*
     * Builds a non-PII metadata payload for Stripe PaymentIntent from an existing held
     * booking.
     */
    private Map<String, String> buildStripeMetadata(BookingEntity booking) {
        return Map.of(
                "bookingId", booking.getId().toString(),
                "employeeId", booking.getEmployee().getId().toString(),
                "treatmentId", booking.getTreatment().getId().toString(),
                "bookingDate", booking.getBookingDate().toString(),
                "startTime", booking.getStartTime().toString(),
                "endTime", booking.getEndTime().toString());
    }

    /*
     * Builds a non-PII metadata payload for Stripe PaymentIntent from a temporary slot
     * hold.
     */
    private Map<String, String> buildStripeMetadata(SlotHoldEntity slotHold) {
        return Map.of(
                "slotHoldId", slotHold.getId().toString(),
                "employeeId", slotHold.getEmployee().getId().toString(),
                "treatmentId", slotHold.getTreatment().getId().toString(),
                "bookingDate", slotHold.getBookingDate().toString(),
                "startTime", slotHold.getStartTime().toString(),
                "endTime", slotHold.getEndTime().toString());
    }

    /*
     * Marks a pending hold as expired in persistence once its expiration window is
     * known to be over.
     *
     * @param booking expired booking hold
     */
    private void markBookingExpired(BookingEntity booking) {
        bookingStateMachine.markBookingExpired(booking);
    }

    /*
     * Detects the transitional case where a booking still has {@code PENDING}
     * status but Stripe has already captured payment successfully.
     *
     * @param booking booking to inspect
     * 
     * @return {@code true} when payment has already been captured
     */
    private boolean isPaidPendingBooking(BookingEntity booking) {
        return bookingStateMachine.isPaidPendingBooking(booking);
    }

    /*
     * Resolves configured booking timezone.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /**
     * Handles audit admin booking created.
     */
    private void auditAdminBookingCreated(BookingEntity booking) {
        bookingAuditService.auditAdminBookingCreated(booking);
    }

    /**
     * Handles audit admin booking updated.
     */
    private void auditAdminBookingUpdated(BookingEntity booking, BookingStatus previousStatus) {
        bookingAuditService.auditAdminBookingUpdated(booking, previousStatus);
    }

    /**
     * Handles audit admin booking cancelled.
     */
    private void auditAdminBookingCancelled(BookingEntity booking, BookingStatus previousStatus) {
        bookingAuditService.auditAdminBookingCancelled(booking, previousStatus);
    }

    /**
     * Handles mask customer email for logs.
     */
    private String maskCustomerEmailForLogs(String email) {
        return bookingAuditService.maskCustomerEmailForLogs(email);
    }

    /**
     * Determines whether h search for logs.
     */
    private String hashSearchForLogs(String search) {
        return bookingAuditService.hashSearchForLogs(search);
    }

    /**
     * Hashes a Stripe PaymentIntent identifier for operational logs.
     */
    private String hashPaymentIntentForLogs(String paymentIntentId) {
        return SensitiveLogSanitizer.hashValue(paymentIntentId);
    }

    /*
     * Converts a temporary slot hold into the compact DTO shape already expected
     * by the existing frontend hold flows.
     */
    private BookingResponseDto toHoldResponseDto(SlotHoldEntity slotHold) {
        return BookingResponseDto.builder()
                .id(slotHold.getId())
                .employeeId(slotHold.getEmployee().getId())
                .employeeName(slotHold.getEmployee().getName())
                .treatmentId(slotHold.getTreatment().getId())
                .treatmentName(slotHold.getTreatment().getName())
                .bookingDate(slotHold.getBookingDate())
                .startTime(slotHold.getStartTime())
                .endTime(slotHold.getEndTime())
                .customerName(slotHold.getCustomerName())
                .customerEmail(slotHold.getCustomerEmail())
                .customerPhone(slotHold.getCustomerPhone())
                .status(BookingStatus.PENDING)
                .expiresAt(slotHold.getExpiresAt())
                .stripePaymentIntentId(slotHold.getStripePaymentIntentId())
                .stripePaymentStatus(slotHold.getStripePaymentStatus())
                .holdAmount(slotHold.getHoldAmount())
                .paymentCapturedAt(slotHold.getPaymentCapturedAt())
                .paymentReleasedAt(slotHold.getPaymentReleasedAt())
                .createdAt(slotHold.getCreatedAt())
                .updatedAt(slotHold.getUpdatedAt())
                .build();
    }

    /*
     * Builds a temporary slot hold entity from selection data.
     */
    private SlotHoldEntity buildSlotHold(
            BookingHoldRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            BigDecimal paymentAmount,
            SlotHoldScope holdScope,
            String clientIp,
            String clientDeviceId,
            LocalDateTime expiresAt) {
        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setActive(true);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(request.getBookingDate());
        slotHold.setStartTime(request.getStartTime());
        slotHold.setEndTime(request.getEndTime());
        slotHold.setHoldScope(holdScope);
        slotHold.setExpiresAt(expiresAt);
        slotHold.setHoldAmount(paymentAmount);
        slotHold.setHoldClientIp(clientIp);
        slotHold.setHoldClientDeviceId(clientDeviceId);
        return slotHold;
    }

    /*
     * Finalizes a paid slot hold into a confirmed booking and removes the
     * temporary hold row.
     */
    private BookingEntity finalizePaidSlotHold(SlotHoldEntity slotHold, String paymentStatus, String source) {
        validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        return bookingStateMachine.finalizePaidSlotHold(slotHold, paymentStatus, source);
    }

    /*
     * Builds a confirmed booking created manually from the admin panel.
     */
    private BookingEntity buildAdminBooking(
            AdminBookingCreateRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            String customerEmail) {
        BookingEntity booking = new BookingEntity();
        booking.setActive(true);
        booking.setEmployee(employee);
        booking.setTreatment(treatment);
        booking.setCustomerName(request.getCustomerName().trim());
        booking.setCustomerEmail(customerEmail);
        booking.setCustomerPhone(request.getCustomerPhone().trim());
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        booking.setHoldAmount(treatment.getPrice());
        booking.setStripePaymentIntentId(null);
        booking.setStripePaymentStatus(null);
        booking.setPaymentCapturedAt(null);
        booking.setPaymentReleasedAt(null);
        booking.setSlotLocked(false);
        return booking;
    }

    /*
     * Ensures the admin-confirm action still refers to the same held slot.
     */
    private boolean matchesAdminHeldSlot(BookingEntity booking, AdminBookingCreateRequestDto request) {
        return bookingValidator.matchesAdminHeldSlot(booking, request);
    }

    /*
     * Ensures the admin-confirm action still refers to the same temporary slot
     * hold.
     */
    private boolean matchesAdminHeldSlot(SlotHoldEntity slotHold, AdminBookingCreateRequestDto request) {
        return bookingValidator.matchesAdminHeldSlot(slotHold, request);
    }

    /*
     * Applies the final confirmed state for a phone booking that started as an
     * admin hold.
     */
    private void applyConfirmedAdminBookingDetails(
            BookingEntity booking,
            AdminBookingCreateRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            String customerEmail) {
        bookingStateMachine.applyConfirmedAdminBookingDetails(booking, request, employee, treatment, customerEmail);
    }
}

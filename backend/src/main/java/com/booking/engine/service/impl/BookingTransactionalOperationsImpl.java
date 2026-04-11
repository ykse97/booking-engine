package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingTransactionalOperations;
import com.booking.engine.service.BookingValidator;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BookingTransactionalOperations}.
 * Provides booking transactional operations related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingTransactionalOperationsImpl implements BookingTransactionalOperations {

    // ---------------------- Constants ----------------------

    private static final int SLOT_HOLD_MINUTES = 10;

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    private final EmployeeRepository employeeRepository;

    private final TreatmentRepository treatmentRepository;

    // ---------------------- Services ----------------------

    private final AvailabilityService availabilityService;

    private final BookingValidator bookingValidator;

    private final BookingStateMachine bookingStateMachine;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public SlotHoldEntity reserveDirectBookingSlot(BookingRequestDto request) {
        EmployeeEntity employee = findActiveEmployeeForUpdate(request.getEmployeeId());
        availabilityService.validateBookingRequest(request);
        bookingValidator.validatePublicCustomerAllowed(
                request.getCustomer().getEmail(),
                request.getCustomer().getPhone());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());

        SlotHoldEntity slotHold = new SlotHoldEntity();
        slotHold.setActive(true);
        slotHold.setEmployee(employee);
        slotHold.setTreatment(treatment);
        slotHold.setBookingDate(request.getBookingDate());
        slotHold.setStartTime(request.getStartTime());
        slotHold.setEndTime(request.getEndTime());
        slotHold.setHoldScope(SlotHoldScope.PUBLIC);
        slotHold.setExpiresAt(LocalDateTime.now(getZoneId()).plusMinutes(SLOT_HOLD_MINUTES));
        slotHold.setHoldAmount(treatment.getPrice());
        slotHold.setCustomerName(request.getCustomer().getName());
        slotHold.setCustomerEmail(request.getCustomer().getEmail());
        slotHold.setCustomerPhone(request.getCustomer().getPhone());
        return slotHoldRepository.save(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public SlotHoldEntity attachStripePaymentToSlotHold(UUID slotHoldId, String paymentIntentId, String paymentStatus) {
        SlotHoldEntity slotHold = findSlotHoldForUpdateOrThrow(slotHoldId);
        slotHold.setStripePaymentIntentId(paymentIntentId);
        slotHold.setStripePaymentStatus(paymentStatus);
        if ("succeeded".equals(paymentStatus)) {
            slotHold.setPaymentCapturedAt(resolveCapturedAt(slotHold));
            slotHold.setPaymentReleasedAt(null);
        }
        return slotHoldRepository.save(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingEntity finalizeDirectBookingFromSlotHold(UUID slotHoldId, String paymentStatus, String source) {
        SlotHoldEntity slotHold = findSlotHoldForUpdateOrThrow(slotHoldId);
        bookingValidator.validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
        return bookingStateMachine.finalizePaidSlotHold(slotHold, paymentStatus, source);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void releaseSlotHold(UUID slotHoldId) {
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(slotHoldId).orElse(null);
        if (slotHold != null) {
            bookingStateMachine.releaseSlotHold(slotHold);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public CheckoutPreparationTarget prepareCheckoutTarget(
            UUID id,
            BookingCheckoutSessionRequestDto request) {
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            bookingValidator.validatePublicSlotHoldAvailability(slotHold);
            bookingValidator.validatePublicCustomerAllowed(
                    request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                    request.getCustomer() != null ? request.getCustomer().getPhone() : null);
            applyCustomerDetails(slotHold, request.getCustomer());
            slotHoldRepository.save(slotHold);
            return new CheckoutPreparationTarget(
                    CheckoutTargetType.SLOT_HOLD,
                    slotHold.getId(),
                    slotHold.getStripePaymentIntentId(),
                    slotHold.getHoldAmount(),
                    slotHold.getCustomerEmail(),
                    buildStripeMetadata(slotHold));
        }

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        bookingValidator.validatePendingBookingAvailability(booking);
        bookingValidator.validatePublicCustomerAllowed(
                request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                request.getCustomer() != null ? request.getCustomer().getPhone() : null);
        applyCustomerDetails(booking, request.getCustomer());
        bookingRepository.save(booking);
        return new CheckoutPreparationTarget(
                CheckoutTargetType.LEGACY_BOOKING,
                booking.getId(),
                booking.getStripePaymentIntentId(),
                booking.getHoldAmount(),
                booking.getCustomerEmail(),
                buildStripeMetadata(booking));
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void persistCheckoutResult(
            CheckoutPreparationTarget target,
            String paymentIntentId,
            String paymentStatus,
            boolean persistNonSuccessStatus) {
        if (target.targetType() == CheckoutTargetType.SLOT_HOLD) {
            SlotHoldEntity slotHold = findSlotHoldForUpdateOrThrow(target.targetId());
            if ("succeeded".equals(paymentStatus)) {
                bookingValidator.validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
            } else {
                bookingValidator.validatePublicSlotHoldAvailability(slotHold);
            }

            if (slotHold.getStripePaymentIntentId() == null
                    || !slotHold.getStripePaymentIntentId().equals(paymentIntentId)) {
                slotHold.setPaymentReleasedAt(null);
            }
            slotHold.setStripePaymentIntentId(paymentIntentId);
            slotHold.setStripePaymentStatus(paymentStatus);
            if ("succeeded".equals(paymentStatus)) {
                slotHold.setPaymentCapturedAt(resolveCapturedAt(slotHold));
            }
            slotHoldRepository.save(slotHold);
            return;
        }

        BookingEntity booking = findBookingForUpdateOrThrow(target.targetId());
        if ("succeeded".equals(paymentStatus)) {
            bookingValidator.validateBookingCanAcceptSuccessfulPayment(booking);
            booking.setStripePaymentIntentId(paymentIntentId);
            bookingStateMachine.applySuccessfulStripePaymentState(booking, paymentStatus, "checkout preparation");
            bookingRepository.save(booking);
            return;
        }

        if (!persistNonSuccessStatus) {
            return;
        }

        bookingValidator.validatePendingBookingAvailability(booking);
        booking.setStripePaymentIntentId(paymentIntentId);
        booking.setStripePaymentStatus(paymentStatus);
        bookingRepository.save(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingEntity confirmHeldBookingAfterPaymentStatus(
            UUID id,
            BookingConfirmationRequestDto request,
            String paymentStatus) {
        SlotHoldEntity slotHold = slotHoldRepository.findByIdForUpdate(id).orElse(null);
        if (slotHold != null) {
            validateStripeIntentExists(slotHold);

            if (!slotHold.getStripePaymentIntentId().equals(request.getPaymentIntentId())) {
                throw new BookingValidationException(
                        "This Stripe payment does not match the current appointment hold.");
            }

            if (slotHold.getCustomerName() == null || slotHold.getCustomerName().isBlank()
                    || slotHold.getCustomerEmail() == null || slotHold.getCustomerEmail().isBlank()) {
                throw new BookingValidationException("Customer details are missing. Please restart checkout.");
            }

            slotHold.setStripePaymentStatus(paymentStatus);

            if ("succeeded".equals(paymentStatus)) {
                bookingValidator.validateSlotHoldCanAcceptSuccessfulPayment(slotHold);
                return bookingStateMachine.finalizePaidSlotHold(slotHold, paymentStatus, "confirm endpoint");
            }

            slotHoldRepository.save(slotHold);
            bookingValidator.validateSlotHoldCanFinalizePayment(slotHold);
            throw new BookingValidationException(
                    "Stripe payment is not completed yet. Please finish payment first.");
        }

        BookingEntity paymentMatchedBooking = bookingRepository.findByStripePaymentIntentIdForUpdate(
                request.getPaymentIntentId()).orElse(null);
        if (paymentMatchedBooking != null
                && !id.equals(paymentMatchedBooking.getId())
                && (paymentMatchedBooking.getStatus() == BookingStatus.CONFIRMED
                        || paymentMatchedBooking.getStatus() == BookingStatus.DONE)) {
            return paymentMatchedBooking;
        }

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        validateStripeIntentExists(booking);

        if (!booking.getStripePaymentIntentId().equals(request.getPaymentIntentId())) {
            throw new BookingValidationException(
                    "This Stripe payment does not match the current appointment hold.");
        }

        if (booking.getCustomerName() == null || booking.getCustomerName().isBlank()
                || booking.getCustomerEmail() == null || booking.getCustomerEmail().isBlank()) {
            throw new BookingValidationException("Customer details are missing. Please restart checkout.");
        }

        if ("succeeded".equals(paymentStatus)) {
            bookingValidator.validateBookingCanAcceptSuccessfulPayment(booking);
            bookingStateMachine.applySuccessfulStripePaymentState(booking, paymentStatus, "confirm endpoint");
            BookingEntity savedBooking = bookingRepository.save(booking);

            log.info("event=booking_confirm action=success bookingId={} paymentIntentHash={} status={}",
                    savedBooking.getId(),
                    hashPaymentIntentForLogs(request.getPaymentIntentId()),
                    savedBooking.getStatus());

            return savedBooking;
        }

        booking.setStripePaymentStatus(paymentStatus);
        bookingValidator.validateBookingCanFinalizePayment(booking);
        throw new BookingValidationException(
                "Stripe payment is not completed yet. Please finish payment first.");
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
     * Finds the requested treatment or throws when it does not exist.
     */
    private TreatmentEntity findTreatmentOrThrow(UUID treatmentId) {
        return treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup outcome=not_found treatmentId={}", treatmentId);
                    return new EntityNotFoundException("Treatment", treatmentId);
                });
    }

    /**
     * Finds the booking with a write lock or throws when it does not exist.
     */
    private BookingEntity findBookingForUpdateOrThrow(UUID id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("event=booking_lookup outcome=not_found_for_update bookingId={}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /**
     * Finds the slot hold with a write lock or throws when it does not exist.
     */
    private SlotHoldEntity findSlotHoldForUpdateOrThrow(UUID id) {
        return slotHoldRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("event=slot_hold_lookup outcome=not_found_for_update slotHoldId={}", id);
                    return new EntityNotFoundException("SlotHold", id);
                });
    }

    /**
     * Ensures that the target booking resource already has a Stripe payment intent.
     */
    private void validateStripeIntentExists(BookingEntity booking) {
        if (booking.getStripePaymentIntentId() == null || booking.getStripePaymentIntentId().isBlank()) {
            throw new IllegalStateException("Booking has no Stripe payment intent");
        }
    }

    /**
     * Ensures that the target booking resource already has a Stripe payment intent.
     */
    private void validateStripeIntentExists(SlotHoldEntity slotHold) {
        if (slotHold.getStripePaymentIntentId() == null || slotHold.getStripePaymentIntentId().isBlank()) {
            throw new IllegalStateException("Slot hold has no Stripe payment intent");
        }
    }

    /**
     * Applies customer details from the request to the payment target.
     */
    private void applyCustomerDetails(BookingEntity booking, BookingRequestDto.CustomerDetailsDto customer) {
        booking.setCustomerName(customer.getName());
        booking.setCustomerEmail(customer.getEmail());
        booking.setCustomerPhone(customer.getPhone());
    }

    /**
     * Applies customer details from the request to the payment target.
     */
    private void applyCustomerDetails(SlotHoldEntity slotHold, BookingRequestDto.CustomerDetailsDto customer) {
        slotHold.setCustomerName(customer.getName());
        slotHold.setCustomerEmail(customer.getEmail());
        slotHold.setCustomerPhone(customer.getPhone());
    }

    /**
     * Builds Stripe metadata for the payment target.
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

    /**
     * Builds Stripe metadata for the payment target.
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

    /**
     * Resolves the timestamp that should be treated as payment capture time.
     */
    private LocalDateTime resolveCapturedAt(SlotHoldEntity slotHold) {
        return slotHold.getPaymentCapturedAt() != null
                ? slotHold.getPaymentCapturedAt()
                : LocalDateTime.now(getZoneId());
    }

    /**
     * Resolves the configured booking timezone as a {@link ZoneId}.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /**
     * Hashes a Stripe PaymentIntent identifier for operational logs.
     */
    private String hashPaymentIntentForLogs(String paymentIntentId) {
        return SensitiveLogSanitizer.hashValue(paymentIntentId);
    }
}

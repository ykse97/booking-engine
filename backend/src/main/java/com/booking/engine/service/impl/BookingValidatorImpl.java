package com.booking.engine.service.impl;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.SlotHoldScope;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.security.SensitiveLogSanitizer;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingStateMachine;
import com.booking.engine.service.BookingValidator;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link BookingValidator}.
 * Provides booking validator related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingValidatorImpl implements BookingValidator {

    // ---------------------- Constants ----------------------

    private static final int MAX_ACTIVE_HOLDS_PER_IP = 2;
    private static final int MAX_ACTIVE_HOLDS_PER_DEVICE = 2;
    private static final String PUBLIC_BOOKING_UNAVAILABLE_MESSAGE =
            "Booking through the website is temporarily unavailable. Please contact the barbershop directly.";
    private static final String HOLD_LIMIT_IP_MESSAGE =
            "This connection already has two active appointment holds. Please complete or release an existing hold before selecting another slot.";
    private static final String HOLD_LIMIT_DEVICE_MESSAGE =
            "This device already has two active appointment holds. Please complete or release an existing hold before selecting another slot.";
    private static final String ADMIN_HOLD_INVALID_MESSAGE =
            "This admin-held slot is no longer available. Please choose the time again.";

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    // ---------------------- Services ----------------------

    private final BookingBlacklistService bookingBlacklistService;

    private final BookingStateMachine bookingStateMachine;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;
    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicCustomerAllowed(String email, String phone) {
        if (bookingBlacklistService.isBlockedCustomer(email, phone)) {
            log.warn(
                    "event=booking_validation outcome=blocked_customer emailMask={} phoneHash={}",
                    maskEmailForLogs(email),
                    hashPhoneForLogs(phone));
            throw new BookingValidationException(PUBLIC_BOOKING_UNAVAILABLE_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateHoldLimit(String clientIp, String clientDeviceId) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        long bookingIpHolds = clientIp != null
                ? bookingRepository.countActiveUnpaidHoldsByClientIp(clientIp, BookingStatus.PENDING, now)
                : 0L;
        long slotHoldIpCount = clientIp != null
                ? slotHoldRepository.countActiveByScopeAndHoldClientIp(SlotHoldScope.PUBLIC, clientIp, now)
                : 0L;
        if (clientIp != null && bookingIpHolds + slotHoldIpCount >= MAX_ACTIVE_HOLDS_PER_IP) {
            log.warn(
                    "event=booking_hold_limit outcome=ip_limit_reached clientIpHash={} bookingHolds={} slotHolds={}",
                    hashValueForLogs(clientIp),
                    bookingIpHolds,
                    slotHoldIpCount);
            throw new BookingValidationException(HOLD_LIMIT_IP_MESSAGE);
        }

        long bookingDeviceHolds = clientDeviceId != null
                ? bookingRepository.countActiveUnpaidHoldsByClientDeviceId(clientDeviceId, BookingStatus.PENDING, now)
                : 0L;
        long slotHoldDeviceCount = clientDeviceId != null
                ? slotHoldRepository.countActiveByScopeAndHoldClientDeviceId(
                        SlotHoldScope.PUBLIC,
                        clientDeviceId,
                        now)
                : 0L;
        if (clientDeviceId != null && bookingDeviceHolds + slotHoldDeviceCount >= MAX_ACTIVE_HOLDS_PER_DEVICE) {
            log.warn(
                    "event=booking_hold_limit outcome=device_limit_reached clientDeviceHash={} bookingHolds={} slotHolds={}",
                    hashValueForLogs(clientDeviceId),
                    bookingDeviceHolds,
                    slotHoldDeviceCount);
            throw new BookingValidationException(HOLD_LIMIT_DEVICE_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePendingBookingAvailability(BookingEntity booking) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BookingValidationException("This appointment has already been confirmed.");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException("This appointment has already been cancelled.");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("This appointment has already been completed.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(now)) {
            bookingStateMachine.markBookingExpired(booking);
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }

        validateHeldBookingSlotStillAvailable(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicSlotHoldAvailability(SlotHoldEntity slotHold) {
        if (slotHold.getHoldScope() != SlotHoldScope.PUBLIC) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        validateActiveSlotHold(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicSlotHoldCancellation(SlotHoldEntity slotHold) {
        if (slotHold.getHoldScope() != SlotHoldScope.PUBLIC) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        if ("succeeded".equals(slotHold.getStripePaymentStatus()) || slotHold.getPaymentCapturedAt() != null) {
            throw new BookingValidationException("Paid bookings can only be updated from the admin panel.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateBookingCanFinalizePayment(BookingEntity booking) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return;
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException("This appointment has already been cancelled.");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("This appointment has already been completed.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        if (bookingStateMachine.isPaidPendingBooking(booking)) {
            return;
        }

        if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(now)) {
            bookingStateMachine.markBookingExpired(booking);
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSlotHoldCanFinalizePayment(SlotHoldEntity slotHold) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (!Boolean.TRUE.equals(slotHold.getActive())) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        if (slotHold.getExpiresAt() == null || !slotHold.getExpiresAt().isAfter(now)) {
            bookingStateMachine.releaseSlotHold(slotHold);
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }

        validateSlotHoldStillAvailable(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateBookingCanAcceptSuccessfulPayment(BookingEntity booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException("This appointment has already been cancelled.");
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("This appointment has already been completed.");
        }

        validateHeldBookingSlotStillAvailable(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSlotHoldCanAcceptSuccessfulPayment(SlotHoldEntity slotHold) {
        if (!Boolean.TRUE.equals(slotHold.getActive())) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        validateSlotHoldStillAvailable(slotHold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePublicCancellation(BookingEntity booking, UUID id) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.warn("event=booking_cancel outcome=already_cancelled bookingId={}", id);
            throw new IllegalStateException("Booking is already canceled");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("This booking can no longer be changed.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException("Only pending booking holds can be cancelled from the public site.");
        }

        if (booking.getPaymentCapturedAt() != null || "succeeded".equals(booking.getStripePaymentStatus())) {
            throw new BookingValidationException("Paid bookings can only be updated from the admin panel.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminCancellation(BookingEntity booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException("This booking has already been cancelled.");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException("This booking has already expired.");
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("Completed bookings cannot be cancelled.");
        }

        if (booking.getStatus() == BookingStatus.PENDING && !bookingStateMachine.isPaidPendingBooking(booking)) {
            validatePendingBookingAvailability(booking);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminHoldSessionId(String adminHoldSessionId) {
        if (adminHoldSessionId == null || adminHoldSessionId.trim().isBlank()) {
            throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminHoldOwnership(BookingEntity booking, String adminHoldSessionId) {
        if (!bookingStateMachine.matchesAdminHoldSession(booking, adminHoldSessionId)) {
            throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminSlotHoldOwnership(SlotHoldEntity slotHold, String adminHoldSessionId) {
        if (!bookingStateMachine.matchesAdminHoldSession(slotHold, adminHoldSessionId)) {
            throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminHoldAvailability(BookingEntity booking) {
        if (!bookingStateMachine.isAdminPanelHold(booking) || bookingStateMachine.isPaidPendingBooking(booking)) {
            throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
        }

        try {
            validatePendingBookingAvailability(booking);
        } catch (BookingValidationException exception) {
            throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateAdminSlotHoldAvailability(SlotHoldEntity slotHold) {
        if (!bookingStateMachine.isAdminPanelHold(slotHold)) {
            throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
        }

        try {
            validateActiveSlotHold(slotHold);
        } catch (BookingValidationException exception) {
            throw new BookingValidationException(ADMIN_HOLD_INVALID_MESSAGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean slotDefinitionChanged(BookingEntity booking, AdminBookingUpdateRequestDto request) {
        return !booking.getEmployee().getId().equals(request.getEmployeeId())
                || !booking.getTreatment().getId().equals(request.getTreatmentId())
                || !booking.getBookingDate().equals(request.getBookingDate())
                || !booking.getStartTime().equals(request.getStartTime())
                || !booking.getEndTime().equals(request.getEndTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldValidateAdminBookingSlot(BookingStatus status) {
        return status == BookingStatus.CONFIRMED
                || status == BookingStatus.DONE
                || status == BookingStatus.CANCELLED
                || status == BookingStatus.PENDING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matchesAdminHeldSlot(BookingEntity booking, AdminBookingCreateRequestDto request) {
        return booking.getEmployee().getId().equals(request.getEmployeeId())
                && booking.getTreatment().getId().equals(request.getTreatmentId())
                && booking.getBookingDate().equals(request.getBookingDate())
                && booking.getStartTime().equals(request.getStartTime())
                && booking.getEndTime().equals(request.getEndTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matchesAdminHeldSlot(SlotHoldEntity slotHold, AdminBookingCreateRequestDto request) {
        return slotHold.getEmployee().getId().equals(request.getEmployeeId())
                && slotHold.getTreatment().getId().equals(request.getTreatmentId())
                && slotHold.getBookingDate().equals(request.getBookingDate())
                && slotHold.getStartTime().equals(request.getStartTime())
                && slotHold.getEndTime().equals(request.getEndTime());
    }
    // ---------------------- Private Methods ----------------------

    /**
     * Validates that the slot hold is still active and not expired.
     */
    private void validateActiveSlotHold(SlotHoldEntity slotHold) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (!Boolean.TRUE.equals(slotHold.getActive())) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        if (slotHold.getExpiresAt() == null || !slotHold.getExpiresAt().isAfter(now)) {
            bookingStateMachine.releaseSlotHold(slotHold);
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }

        validateSlotHoldStillAvailable(slotHold);
    }

    /**
     * Validates that the held booking slot is still available before continuing.
     */
    private void validateHeldBookingSlotStillAvailable(BookingEntity booking) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        List<BookingEntity> sameDayBookings = Optional.ofNullable(
                bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                        booking.getEmployee().getId(),
                        booking.getBookingDate(),
                        List.of(
                                BookingStatus.PENDING,
                                BookingStatus.CONFIRMED,
                                BookingStatus.CANCELLED,
                                BookingStatus.DONE)))
                .orElse(List.of());

        BookingEntity conflictingBooking = sameDayBookings.stream()
                .filter(existing -> booking.getId() == null || !booking.getId().equals(existing.getId()))
                .filter(existing -> booking.getStartTime().isBefore(existing.getEndTime())
                        && booking.getEndTime().isAfter(existing.getStartTime()))
                .filter(existing -> existing.getStatus() == BookingStatus.CONFIRMED
                        || existing.getStatus() == BookingStatus.DONE
                        || bookingStateMachine.isBlockingPendingSlot(existing, now)
                        || bookingStateMachine.isLockedCancelledSlot(existing))
                .findFirst()
                .orElse(null);

        if (conflictingBooking == null) {
            return;
        }

        if (bookingStateMachine.isActiveHoldSlot(conflictingBooking, now)) {
            throw new BookingValidationException(
                    "This slot has just been held by another guest. Sorry for the inconvenience.");
        }

        throw new BookingValidationException("This slot has already been booked by someone else.");
    }

    /**
     * Validates that the slot hold still points to an available time slot.
     */
    private void validateSlotHoldStillAvailable(SlotHoldEntity slotHold) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        List<BookingEntity> sameDayBookings = Optional.ofNullable(
                bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                        slotHold.getEmployee().getId(),
                        slotHold.getBookingDate(),
                        List.of(
                                BookingStatus.PENDING,
                                BookingStatus.CONFIRMED,
                                BookingStatus.CANCELLED,
                                BookingStatus.DONE)))
                .orElse(List.of());

        BookingEntity conflictingBooking = sameDayBookings.stream()
                .filter(existing -> slotHold.getStartTime().isBefore(existing.getEndTime())
                        && slotHold.getEndTime().isAfter(existing.getStartTime()))
                .filter(existing -> existing.getStatus() == BookingStatus.CONFIRMED
                        || existing.getStatus() == BookingStatus.DONE
                        || bookingStateMachine.isBlockingPendingSlot(existing, now)
                        || bookingStateMachine.isLockedCancelledSlot(existing))
                .findFirst()
                .orElse(null);

        if (conflictingBooking != null) {
            if (bookingStateMachine.isActiveHoldSlot(conflictingBooking, now)) {
                throw new BookingValidationException(
                        "This slot has just been held by another guest. Sorry for the inconvenience.");
            }

            throw new BookingValidationException("This slot has already been booked by someone else.");
        }

        SlotHoldEntity conflictingSlotHold = Optional.ofNullable(
                slotHoldRepository.findActiveByEmployeeIdAndBookingDate(
                        slotHold.getEmployee().getId(),
                        slotHold.getBookingDate(),
                        now))
                .orElse(List.of())
                .stream()
                .filter(existing -> !slotHold.getId().equals(existing.getId()))
                .filter(existing -> slotHold.getStartTime().isBefore(existing.getEndTime())
                        && slotHold.getEndTime().isAfter(existing.getStartTime()))
                .findFirst()
                .orElse(null);

        if (conflictingSlotHold != null) {
            throw new BookingValidationException(
                    "This slot has just been held by another guest. Sorry for the inconvenience.");
        }
    }

    /**
     * Resolves the configured booking timezone as a {@link ZoneId}.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /**
     * Hashes a free-form value before writing it to operational logs.
     */
    private String hashValueForLogs(String value) {
        return SensitiveLogSanitizer.hashValue(value);
    }

    /**
     * Masks customer email before writing it to operational logs.
     */
    private String maskEmailForLogs(String email) {
        return SensitiveLogSanitizer.maskEmail(email);
    }

    /**
     * Hashes customer phone before writing it to operational logs.
     */
    private String hashPhoneForLogs(String phone) {
        return SensitiveLogSanitizer.hashPhone(phone);
    }
}

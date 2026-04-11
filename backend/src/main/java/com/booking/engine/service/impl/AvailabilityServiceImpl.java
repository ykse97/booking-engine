package com.booking.engine.service.impl;

import com.booking.engine.dto.AvailabilitySlotDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.SlotHoldRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.AvailabilityService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AvailabilityService}.
 * Provides availability related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityServiceImpl implements AvailabilityService {
    // ---------------------- Constants ----------------------

    private static final int SLOT_DURATION_MINUTES = 60;
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_BREAK = "BREAK";
    private static final String STATUS_BOOKED = "BOOKED";
    private static final String STATUS_HELD = "HELD";
    private static final String STATUS_PAST = "PAST";

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;

    private final SlotHoldRepository slotHoldRepository;

    private final EmployeeRepository employeeRepository;

    private final EmployeeDailyScheduleRepository employeeScheduleRepository;

    private final TreatmentRepository treatmentRepository;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateBookingRequest(BookingRequestDto request) {
        log.debug(
                "event=availability_validate_request action=start employeeId={} treatmentId={} bookingDate={} startTime={}",
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());

        validateSlotSelection(
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());

        log.debug(
                "event=availability_validate_request action=success employeeId={} treatmentId={} bookingDate={} startTime={}",
                request.getEmployeeId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSlotSelection(
            UUID employeeId,
            UUID treatmentId,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalTime endTime) {
        validateSlotSelectionExcludingBooking(
                employeeId,
                treatmentId,
                bookingDate,
                startTime,
                endTime,
                null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSlotSelectionExcludingBooking(
            UUID employeeId,
            UUID treatmentId,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalTime endTime,
            UUID ignoredBookingId) {
        EmployeeEntity employee = findActiveEmployee(employeeId);
        ensureActiveTreatment(treatmentId);
        validateEmployeeProvidesTreatment(employee.getId(), treatmentId);

        validateSlotNotInPast(bookingDate, startTime);
        validateWorkingHours(employee.getId(), bookingDate, startTime, endTime);
        validateNoTimeConflicts(employee.getId(), bookingDate, startTime, endTime, ignoredBookingId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AvailabilitySlotDto> getAvailability(UUID employeeId, LocalDate date, UUID treatmentId) {
        EmployeeEntity employee = findActiveEmployee(employeeId);
        ensureActiveTreatment(treatmentId);
        validateEmployeeProvidesTreatment(employee.getId(), treatmentId);

        var workingHoursOpt = employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employee.getId(), date);
        if (workingHoursOpt.isEmpty() || !workingHoursOpt.get().isWorkingDay()) {
            return List.of();
        }

        var workingHours = workingHoursOpt.get();
        LocalTime open = workingHours.getOpenTime();
        LocalTime close = workingHours.getCloseTime();
        LocalTime breakStart = workingHours.getBreakStartTime();
        LocalTime breakEnd = workingHours.getBreakEndTime();
        LocalDateTime now = LocalDateTime.now(getZoneId());

        List<BookingEntity> relevantBookings = getRelevantBookings(employee.getId(), date, now);
        List<SlotHoldEntity> relevantSlotHolds = getRelevantSlotHolds(employee.getId(), date, now);
        List<AvailabilitySlotDto> slots = new ArrayList<>();

        for (LocalTime start = open; !start.plusMinutes(SLOT_DURATION_MINUTES).isAfter(close); start = start
                .plusMinutes(SLOT_DURATION_MINUTES)) {

            LocalTime end = start.plusMinutes(SLOT_DURATION_MINUTES);
            String slotStatus = resolveSlotStatus(
                    date,
                    start,
                    end,
                    breakStart,
                    breakEnd,
                    relevantBookings,
                    relevantSlotHolds,
                    now);

            slots.add(AvailabilitySlotDto.builder()
                    .startTime(start)
                    .endTime(end)
                    .available(STATUS_AVAILABLE.equals(slotStatus))
                    .status(slotStatus)
                    .build());
        }

        return slots;
    }

    // ---------------------- Private Methods ----------------------

    /**
     * Finds an active bookable employee or throws when booking is not allowed.
     */
    private EmployeeEntity findActiveEmployee(UUID employeeId) {
        EmployeeEntity employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BookingValidationException("Employee not found"));

        if (!employee.getActive()) {
            throw new BookingValidationException("Employee is not active");
        }

        if (!Boolean.TRUE.equals(employee.getBookable())) {
            throw new BookingValidationException("Employee is not available for booking");
        }

        return employee;
    }

    /*
     * Finds treatment by ID and validates it exists and is active.
     */
    private void ensureActiveTreatment(UUID treatmentId) {
        var treatment = treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> new BookingValidationException("Treatment not found"));

        if (!treatment.getActive()) {
            throw new BookingValidationException("Treatment is not active");
        }
    }

    /*
     * Validates that the employee is configured to provide the selected service.
     */
    private void validateEmployeeProvidesTreatment(UUID employeeId, UUID treatmentId) {
        if (!employeeRepository.existsActiveEmployeeTreatment(employeeId, treatmentId)) {
            throw new BookingValidationException("This employee does not provide the selected service.");
        }
    }

    /*
     * Validates that booking slot is not in the past.
     */
    private void validateSlotNotInPast(LocalDate bookingDate, LocalTime startTime) {
        LocalDateTime slotDateTime = LocalDateTime.of(bookingDate, startTime);
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (!slotDateTime.isAfter(now)) {
            log.debug("event=availability_validate_slot outcome=in_past bookingDate={} startTime={}", bookingDate,
                    startTime);
            throw new BookingValidationException(
                    "Cannot book a slot in the past. Current time: " + now);
        }
    }

    /*
     * Validates booking time against employee working hours for the specific day.
     */
    private void validateWorkingHours(UUID employeeId, LocalDate bookingDate, LocalTime startTime, LocalTime endTime) {
        var workingHoursOpt = employeeScheduleRepository.findByEmployeeIdAndWorkingDate(employeeId, bookingDate);

        if (workingHoursOpt.isEmpty()) {
            throw new BookingValidationException(bookingDate + " is not a working day");
        }

        var workingHours = workingHoursOpt.get();

        if (!workingHours.isWorkingDay()) {
            throw new BookingValidationException(bookingDate + " is not a working day");
        }

        LocalTime openTime = workingHours.getOpenTime();
        LocalTime closeTime = workingHours.getCloseTime();

        if (startTime.isBefore(openTime)) {
            throw new BookingValidationException(
                    "Booking starts before employee working time. Employee starts at " + openTime);
        }

        if (endTime.isAfter(closeTime)) {
            throw new BookingValidationException(
                    "Booking ends after employee working time. Employee ends at " + closeTime);
        }

        if (overlapsBreak(startTime, endTime, workingHours.getBreakStartTime(), workingHours.getBreakEndTime())) {
            throw new BookingValidationException("Selected slot overlaps the employee break time.");
        }
    }

    /*
     * Validates that time slot has no conflicts with existing bookings or active
     * slot holds.
     */
    private void validateNoTimeConflicts(UUID employeeId, LocalDate date,
            LocalTime newStart, LocalTime newEnd, UUID ignoredBookingId) {
        LocalDateTime now = LocalDateTime.now(getZoneId());
        List<BookingEntity> relevantBookings = getRelevantBookings(employeeId, date, now, ignoredBookingId);
        List<SlotHoldEntity> relevantSlotHolds = getRelevantSlotHolds(employeeId, date, now);
        BookingEntity conflictingBooking = findConflictingBooking(newStart, newEnd, relevantBookings);

        if (conflictingBooking != null) {
            if (isActiveHold(conflictingBooking, now)) {
                throw new BookingValidationException(
                        "This slot has just been held by another guest. Sorry for the inconvenience.");
            }

            throw new BookingValidationException("This slot has already been booked by someone else.");
        }

        SlotHoldEntity conflictingHold = findConflictingSlotHold(newStart, newEnd, relevantSlotHolds);
        if (conflictingHold != null) {
            throw new BookingValidationException(
                    "This slot has just been held by another guest. Sorry for the inconvenience.");
        }

        log.debug("event=availability_validate_conflicts action=clear employeeId={} bookingDate={}", employeeId, date);
    }

    /*
     * Retrieves relevant bookings for employee on a specific date.
     * Expired pending holds are ignored immediately, even before the scheduler
     * updates their persisted status.
     */
    private List<BookingEntity> getRelevantBookings(UUID employeeId, LocalDate date, LocalDateTime now) {
        return getRelevantBookings(employeeId, date, now, null);
    }

    /*
     * Retrieves active temporary slot holds for employee on a specific date.
     */
    private List<SlotHoldEntity> getRelevantSlotHolds(UUID employeeId, LocalDate date, LocalDateTime now) {
        return slotHoldRepository.findActiveByEmployeeIdAndBookingDate(employeeId, date, now);
    }

    /*
     * Retrieves relevant bookings for employee on a specific date.
     * Expired pending holds are ignored immediately, even before the scheduler
     * updates their persisted status. When a booking is being edited by the
     * admin, it can be excluded from conflict checks by id.
     */
    private List<BookingEntity> getRelevantBookings(UUID employeeId, LocalDate date, LocalDateTime now,
            UUID ignoredBookingId) {
        return bookingRepository.findByEmployeeIdAndBookingDateAndStatusIn(
                employeeId,
                date,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.DONE))
                .stream()
                .filter(booking -> ignoredBookingId == null || !ignoredBookingId.equals(booking.getId()))
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED
                        || booking.getStatus() == BookingStatus.DONE
                        || isBlockingPendingBooking(booking, now)
                        || isLockedCancelledBooking(booking))
                .toList();
    }

    /*
     * Scans existing bookings and returns the first entry whose time range overlaps
     * the requested slot boundaries.
     *
     * @param start requested slot start time
     * 
     * @param end requested slot end time
     * 
     * @param existingBookings already relevant bookings for the day
     * 
     * @return conflicting booking or {@code null} when none exists
     */
    private BookingEntity findConflictingBooking(LocalTime start, LocalTime end, List<BookingEntity> existingBookings) {
        return existingBookings.stream()
                .filter(existing -> start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime()))
                .findFirst()
                .orElse(null);
    }

    /*
     * Scans active slot holds and returns the first overlapping reservation.
     */
    private SlotHoldEntity findConflictingSlotHold(LocalTime start, LocalTime end, List<SlotHoldEntity> existingHolds) {
        return existingHolds.stream()
                .filter(existing -> start.isBefore(existing.getEndTime()) && end.isAfter(existing.getStartTime()))
                .findFirst()
                .orElse(null);
    }

    /*
     * Checks whether a candidate slot overlaps the configured employee break
     * window.
     * Missing break boundaries are treated as no break configured for the day.
     *
     * @param start candidate slot start time
     * 
     * @param end candidate slot end time
     * 
     * @param breakStart configured break start
     * 
     * @param breakEnd configured break end
     * 
     * @return {@code true} when the slot intersects the break
     */
    private boolean overlapsBreak(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd) {
        if (breakStart == null || breakEnd == null) {
            return false;
        }
        return start.isBefore(breakEnd) && end.isAfter(breakStart);
    }

    /*
     * Resolves the public slot status by checking break overlap, existing booking
     * conflicts, active temporary holds, and whether the slot already belongs to
     * the past.
     *
     * @param date target booking date
     * 
     * @param start slot start time
     * 
     * @param end slot end time
     * 
     * @param breakStart optional break start
     * 
     * @param breakEnd optional break end
     * 
     * @param existingBookings day bookings and active holds
     * 
     * @param now current timestamp in booking timezone
     * 
     * @return slot status constant for the API response
     */
    private String resolveSlotStatus(
            LocalDate date,
            LocalTime start,
            LocalTime end,
            LocalTime breakStart,
            LocalTime breakEnd,
            List<BookingEntity> existingBookings,
            List<SlotHoldEntity> existingSlotHolds,
            LocalDateTime now) {
        if (overlapsBreak(start, end, breakStart, breakEnd)) {
            return STATUS_BREAK;
        }

        BookingEntity conflictingBooking = findConflictingBooking(start, end, existingBookings);
        if (conflictingBooking != null) {
            return hasSlotEnded(date, end, now)
                    ? STATUS_PAST
                    : (isActiveHold(conflictingBooking, now) ? STATUS_HELD : STATUS_BOOKED);
        }

        if (findConflictingSlotHold(start, end, existingSlotHolds) != null) {
            return hasSlotEnded(date, end, now) ? STATUS_PAST : STATUS_HELD;
        }

        return hasSlotStarted(date, start, now) ? STATUS_PAST : STATUS_AVAILABLE;
    }

    /*
     * Determines whether the slot start moment is at or before the current time,
     * meaning the slot should no longer be offered as future availability.
     *
     * @param date slot date
     * 
     * @param start slot start time
     * 
     * @param now current timestamp
     * 
     * @return {@code true} when the slot has started
     */
    private boolean hasSlotStarted(LocalDate date, LocalTime start, LocalDateTime now) {
        LocalDateTime slotStart = LocalDateTime.of(date, start);
        return !slotStart.isAfter(now);
    }

    /*
     * Determines whether the slot end moment is already in the past so a booked
     * slot can be shown as historical rather than simply blocked.
     *
     * @param date slot date
     * 
     * @param end slot end time
     * 
     * @param now current timestamp
     * 
     * @return {@code true} when the slot has ended
     */
    private boolean hasSlotEnded(LocalDate date, LocalTime end, LocalDateTime now) {
        LocalDateTime slotEnd = LocalDateTime.of(date, end);
        return !slotEnd.isAfter(now);
    }

    /*
     * Checks whether a pending booking should still block availability because it
     * either already has successful payment captured or is still inside its active
     * hold window.
     *
     * @param booking pending booking candidate
     * 
     * @param now current timestamp
     * 
     * @return {@code true} when the pending booking should block the slot
     */
    private boolean isBlockingPendingBooking(BookingEntity booking, LocalDateTime now) {
        return booking.getStatus() == BookingStatus.PENDING
                && (isPaidPendingBooking(booking) || isActiveHold(booking, now));
    }

    /*
     * Detects admin-cancelled bookings that must continue blocking the slot on
     * the public site.
     *
     * @param booking booking to inspect
     * 
     * @return {@code true} when the cancelled booking still reserves the slot
     */
    private boolean isLockedCancelledBooking(BookingEntity booking) {
        return booking.getStatus() == BookingStatus.CANCELLED
                && Boolean.TRUE.equals(booking.getSlotLocked());
    }

    /*
     * Detects the special case where a booking still carries {@code PENDING}
     * status but Stripe payment has already been completed successfully.
     *
     * @param booking booking to inspect
     * 
     * @return {@code true} when payment is already captured
     */
    private boolean isPaidPendingBooking(BookingEntity booking) {
        return booking.getPaymentCapturedAt() != null || "succeeded".equals(booking.getStripePaymentStatus());
    }

    /*
     * Checks whether a temporary unpaid slot hold is still active and should
     * reserve the slot from other customers.
     *
     * @param booking pending booking hold
     * 
     * @param now current timestamp
     * 
     * @return {@code true} when the hold has not expired yet
     */
    private boolean isActiveHold(BookingEntity booking, LocalDateTime now) {
        return booking.getStatus() == BookingStatus.PENDING
                && !isPaidPendingBooking(booking)
                && booking.getExpiresAt() != null
                && booking.getExpiresAt().isAfter(now);
    }

    /*
     * Resolves the application booking timezone once so all date-time comparisons
     * inside availability calculations use a consistent clock.
     *
     * @return booking timezone
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }
}

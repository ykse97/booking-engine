package com.booking.engine.service;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.AvailabilitySlotDto;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for booking availability checks and slot validation rules.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
public interface AvailabilityService {

    /**
     * Validates all booking constraints including:
     * - Barber and treatment existence and active status
     * - Booking date/time not in the past
     * - Working hours compliance
     * - Slot availability (no conflicts)
     *
     * @param request the booking request to validate
     * @throws com.booking.engine.exception.BookingValidationException if validation
     *                                                                 fails
     */
    void validateBookingRequest(BookingRequestDto request);

    /**
     * Validates a selected booking slot without requiring customer or payment
     * details. Used by the temporary hold flow before payment is collected.
     *
     * @param barberId barber identifier
     * @param treatmentId treatment identifier
     * @param bookingDate selected booking date
     * @param startTime selected slot start time
     * @param endTime selected slot end time
     */
    void validateSlotSelection(
            UUID barberId,
            UUID treatmentId,
            LocalDate bookingDate,
            LocalTime startTime,
            LocalTime endTime);

    /**
     * Calculates fixed one-hour availability slots for a barber on a specific
     * date.
     *
     * @param barberId    barber identifier
     * @param date        target date
     * @param treatmentId treatment identifier
     * @return list of slots with availability flags
     */
    List<AvailabilitySlotDto> getAvailability(UUID barberId, LocalDate date, UUID treatmentId);
}

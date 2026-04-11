package com.booking.engine.service;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import java.util.UUID;

/**
 * Service contract for public booking operations.
 * Defines public booking related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface PublicBookingService {

    /**
     * Retrieves booking by id.
     *
     * @param id identifier
     * @return result value
     */
    BookingResponseDto getBookingById(UUID id);

    /**
     * Creates
     *
     * @param request request payload
     * @return result value
     */
    BookingResponseDto create(BookingRequestDto request);

    /**
     * Holds slot.
     *
     * @param request request payload
     * @param clientIp client IP address
     * @param clientDeviceId client device identifier
     * @return result value
     */
    BookingResponseDto holdSlot(BookingHoldRequestDto request, String clientIp, String clientDeviceId);

    /**
     * Validates held booking checkout.
     *
     * @param id identifier
     * @param request request payload
     */
    void validateHeldBookingCheckout(UUID id, BookingCheckoutValidationRequestDto request);

    /**
     * Executes prepare held booking checkout.
     *
     * @param id identifier
     * @param request request payload
     * @return result value
     */
    BookingCheckoutSessionResponseDto prepareHeldBookingCheckout(
            UUID id,
            BookingCheckoutSessionRequestDto request);

    /**
     * Executes confirm held booking.
     *
     * @param id identifier
     * @param request request payload
     * @return result value
     */
    BookingResponseDto confirmHeldBooking(UUID id, BookingConfirmationRequestDto request);

    /**
     * Executes cancel booking.
     *
     * @param id identifier
     */
    void cancelBooking(UUID id);
}

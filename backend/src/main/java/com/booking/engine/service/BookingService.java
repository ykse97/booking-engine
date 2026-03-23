package com.booking.engine.service;

import java.util.UUID;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;

/**
 * Service contract for booking lifecycle operations.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public interface BookingService {

    /**
     * Retrieves booking by identifier.
     *
     * @param id booking identifier
     * @return booking DTO
     */
    BookingResponseDto getBookingById(UUID id);

    /**
     * Creates a new booking.
     *
     * @param request booking request payload
     * @return created booking DTO
     */
    BookingResponseDto create(BookingRequestDto request);

    /**
     * Creates a free admin booking without Stripe payment.
     * Used for phone bookings entered by salon staff.
     *
     * @param request admin booking payload
     * @return created confirmed booking DTO
     */
    BookingResponseDto createAdminBooking(AdminBookingCreateRequestDto request);

    /**
     * Creates a temporary 10-minute hold for a selected slot before payment.
     *
     * @param request slot hold request payload
     * @param clientIp resolved client IP or forwarded proxy IP
     * @param clientDeviceId browser-generated persistent device identifier
     * @return pending booking DTO with expiration timestamp
     */
    BookingResponseDto holdSlot(BookingHoldRequestDto request, String clientIp, String clientDeviceId);

    /**
     * Uses a Stripe ConfirmationToken produced by the frontend checkout modal
     * to create and confirm an immediate payment for a previously held slot.
     *
     * @param id held booking identifier
     * @param request customer details and confirmation token
     * @return checkout session payload
     */
    BookingCheckoutSessionResponseDto prepareHeldBookingCheckout(
            UUID id,
            BookingCheckoutSessionRequestDto request);

    /**
     * Finalizes a previously held slot after Stripe payment is completed.
     * The final {@code CONFIRMED} status is set only after Stripe webhook sync.
     *
     * @param id held booking identifier
     * @param request confirmation payload with Stripe PaymentIntent reference
     * @return latest booking DTO after client-side checkout confirmation
     */
    BookingResponseDto confirmHeldBooking(UUID id, BookingConfirmationRequestDto request);

    /**
     * Cancels an unpaid public booking hold by identifier.
     *
     * @param id booking identifier
     */
    void cancelBooking(UUID id);

    /**
     * Returns admin booking overview with optional contact search.
     *
     * @param search customer name / phone / email query
     * @return matching bookings plus confirmed count
     */
    AdminBookingListResponseDto getAdminBookings(String search);

    /**
     * Cancels a booking from the admin panel.
     *
     * @param id booking identifier
     * @return updated booking DTO
     */
    BookingResponseDto cancelBookingByAdmin(UUID id);

    /**
     * Synchronizes booking payment state from Stripe webhook callbacks.
     *
     * @param paymentIntentId Stripe PaymentIntent identifier
     * @param paymentStatus Stripe status value
     * @param eventType Stripe event type
     */
    void syncStripePaymentIntentFromWebhook(String paymentIntentId, String paymentStatus, String eventType);

}

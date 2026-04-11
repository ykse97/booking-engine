package com.booking.engine.service;

import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.SlotHoldEntity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Service contract for booking transactional operations operations.
 * Defines booking transactional operations related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface BookingTransactionalOperations {

    record CheckoutPreparationTarget(
            CheckoutTargetType targetType,
            UUID targetId,
            String existingPaymentIntentId,
            BigDecimal holdAmount,
            String customerEmail,
            Map<String, String> stripeMetadata) {
    }

    enum CheckoutTargetType {
        SLOT_HOLD,
        LEGACY_BOOKING
    }

    /**
     * Executes reserve direct booking slot.
     *
     * @param request request payload
     * @return result value
     */
    SlotHoldEntity reserveDirectBookingSlot(BookingRequestDto request);

    /**
     * Executes attach stripe payment to slot hold.
     *
     * @param slotHoldId slot-hold identifier
     * @param paymentIntentId payment intent identifier
     * @param paymentStatus payment status value
     * @return result value
     */
    SlotHoldEntity attachStripePaymentToSlotHold(UUID slotHoldId, String paymentIntentId, String paymentStatus);

    /**
     * Executes finalize direct booking from slot hold.
     *
     * @param slotHoldId slot-hold identifier
     * @param paymentStatus payment status value
     * @param source source value
     * @return result value
     */
    BookingEntity finalizeDirectBookingFromSlotHold(UUID slotHoldId, String paymentStatus, String source);

    /**
     * Releases slot hold.
     *
     * @param slotHoldId slot-hold identifier
     */
    void releaseSlotHold(UUID slotHoldId);

    /**
     * Executes prepare checkout target.
     *
     * @param id identifier
     * @param request request payload
     * @return result value
     */
    CheckoutPreparationTarget prepareCheckoutTarget(
            UUID id,
            BookingCheckoutSessionRequestDto request);

    /**
     * Executes persist checkout result.
     *
     * @param target target value
     * @param paymentIntentId payment intent identifier
     * @param paymentStatus payment status value
     * @param persistNonSuccessStatus persist non success status value
     */
    void persistCheckoutResult(
            CheckoutPreparationTarget target,
            String paymentIntentId,
            String paymentStatus,
            boolean persistNonSuccessStatus);

    /**
     * Executes confirm held booking after payment status.
     *
     * @param id identifier
     * @param request request payload
     * @param paymentStatus payment status value
     * @return result value
     */
    BookingEntity confirmHeldBookingAfterPaymentStatus(
            UUID id,
            BookingConfirmationRequestDto request,
            String paymentStatus);
}

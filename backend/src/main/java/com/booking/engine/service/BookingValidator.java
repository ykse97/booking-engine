package com.booking.engine.service;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.SlotHoldEntity;
import java.util.UUID;

/**
 * Service contract for booking validator operations.
 * Defines booking validator related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface BookingValidator {

    /**
     * Validates public customer allowed.
     *
     * @param email email value
     * @param phone phone value
     */
    void validatePublicCustomerAllowed(String email, String phone);

    /**
     * Validates hold limit.
     *
     * @param clientIp client IP address
     * @param clientDeviceId client device identifier
     */
    void validateHoldLimit(String clientIp, String clientDeviceId);

    /**
     * Validates pending booking availability.
     *
     * @param booking booking entity
     */
    void validatePendingBookingAvailability(BookingEntity booking);

    /**
     * Validates public slot hold availability.
     *
     * @param slotHold slot-hold entity
     */
    void validatePublicSlotHoldAvailability(SlotHoldEntity slotHold);

    /**
     * Validates public slot hold cancellation.
     *
     * @param slotHold slot-hold entity
     */
    void validatePublicSlotHoldCancellation(SlotHoldEntity slotHold);

    /**
     * Validates booking can finalize payment.
     *
     * @param booking booking entity
     */
    void validateBookingCanFinalizePayment(BookingEntity booking);

    /**
     * Validates slot hold can finalize payment.
     *
     * @param slotHold slot-hold entity
     */
    void validateSlotHoldCanFinalizePayment(SlotHoldEntity slotHold);

    /**
     * Validates booking can accept successful payment.
     *
     * @param booking booking entity
     */
    void validateBookingCanAcceptSuccessfulPayment(BookingEntity booking);

    /**
     * Validates slot hold can accept successful payment.
     *
     * @param slotHold slot-hold entity
     */
    void validateSlotHoldCanAcceptSuccessfulPayment(SlotHoldEntity slotHold);

    /**
     * Validates public cancellation.
     *
     * @param booking booking entity
     * @param id identifier
     */
    void validatePublicCancellation(BookingEntity booking, UUID id);

    /**
     * Validates admin cancellation.
     *
     * @param booking booking entity
     */
    void validateAdminCancellation(BookingEntity booking);

    /**
     * Validates admin hold session id.
     *
     * @param adminHoldSessionId admin hold session identifier
     */
    void validateAdminHoldSessionId(String adminHoldSessionId);

    /**
     * Validates admin hold ownership.
     *
     * @param booking booking entity
     * @param adminHoldSessionId admin hold session identifier
     */
    void validateAdminHoldOwnership(BookingEntity booking, String adminHoldSessionId);

    /**
     * Validates admin slot hold ownership.
     *
     * @param slotHold slot-hold entity
     * @param adminHoldSessionId admin hold session identifier
     */
    void validateAdminSlotHoldOwnership(SlotHoldEntity slotHold, String adminHoldSessionId);

    /**
     * Validates admin hold availability.
     *
     * @param booking booking entity
     */
    void validateAdminHoldAvailability(BookingEntity booking);

    /**
     * Validates admin slot hold availability.
     *
     * @param slotHold slot-hold entity
     */
    void validateAdminSlotHoldAvailability(SlotHoldEntity slotHold);

    /**
     * Executes slot definition changed.
     *
     * @param booking booking entity
     * @param request request payload
     * @return true when slot definition changed succeeds
     */
    boolean slotDefinitionChanged(BookingEntity booking, AdminBookingUpdateRequestDto request);

    /**
     * Executes should validate admin booking slot.
     *
     * @param status status value
     * @return true when should validate admin booking slot succeeds
     */
    boolean shouldValidateAdminBookingSlot(BookingStatus status);

    /**
     * Executes matches admin held slot.
     *
     * @param booking booking entity
     * @param request request payload
     * @return true when matches admin held slot succeeds
     */
    boolean matchesAdminHeldSlot(BookingEntity booking, AdminBookingCreateRequestDto request);

    /**
     * Executes matches admin held slot.
     *
     * @param slotHold slot-hold entity
     * @param request request payload
     * @return true when matches admin held slot succeeds
     */
    boolean matchesAdminHeldSlot(SlotHoldEntity slotHold, AdminBookingCreateRequestDto request);
}

package com.booking.engine.service;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.SlotHoldEntity;
import com.booking.engine.entity.TreatmentEntity;
import java.time.LocalDateTime;

/**
 * Service contract for booking state machine operations.
 * Defines booking state machine related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface BookingStateMachine {

    String ADMIN_HOLD_CLIENT_IP = "admin-panel";
    String ADMIN_HOLD_DEVICE_PREFIX = "admin-panel:";

    /**
     * Applies successful stripe payment state.
     *
     * @param booking booking entity
     * @param paymentStatus payment status value
     * @param source source value
     */
    void applySuccessfulStripePaymentState(BookingEntity booking, String paymentStatus, String source);

    /**
     * Applies failed stripe payment state.
     *
     * @param booking booking entity
     * @param paymentStatus payment status value
     */
    void applyFailedStripePaymentState(BookingEntity booking, String paymentStatus);

    /**
     * Applies failed stripe payment state.
     *
     * @param slotHold slot-hold entity
     * @param paymentStatus payment status value
     */
    void applyFailedStripePaymentState(SlotHoldEntity slotHold, String paymentStatus);

    /**
     * Executes cancel public booking.
     *
     * @param booking booking entity
     */
    void cancelPublicBooking(BookingEntity booking);

    /**
     * Executes cancel by admin.
     *
     * @param booking booking entity
     */
    void cancelByAdmin(BookingEntity booking);

    /**
     * Applies admin update state.
     *
     * @param booking booking entity
     * @param status status value
     */
    void applyAdminUpdateState(BookingEntity booking, BookingStatus status);

    /**
     * Marks booking expired.
     *
     * @param booking booking entity
     * @return result value
     */
    BookingEntity markBookingExpired(BookingEntity booking);

    /**
     * Releases admin hold.
     *
     * @param booking booking entity
     */
    void releaseAdminHold(BookingEntity booking);

    /**
     * Releases slot hold.
     *
     * @param slotHold slot-hold entity
     */
    void releaseSlotHold(SlotHoldEntity slotHold);

    /**
     * Executes finalize paid slot hold.
     *
     * @param slotHold slot-hold entity
     * @param paymentStatus payment status value
     * @param source source value
     * @return result value
     */
    BookingEntity finalizePaidSlotHold(SlotHoldEntity slotHold, String paymentStatus, String source);

    /**
     * Applies confirmed admin booking details.
     *
     * @param booking booking entity
     * @param request request payload
     * @param employee employee entity
     * @param treatment treatment entity
     * @param customerEmail customer email value
     */
    void applyConfirmedAdminBookingDetails(
            BookingEntity booking,
            AdminBookingCreateRequestDto request,
            EmployeeEntity employee,
            TreatmentEntity treatment,
            String customerEmail);

    /**
     * Checks whether paid pending booking.
     *
     * @param booking booking entity
     * @return true when is paid pending booking succeeds
     */
    boolean isPaidPendingBooking(BookingEntity booking);

    /**
     * Checks whether blocking pending slot.
     *
     * @param booking booking entity
     * @param now current timestamp
     * @return true when is blocking pending slot succeeds
     */
    boolean isBlockingPendingSlot(BookingEntity booking, LocalDateTime now);

    /**
     * Checks whether active hold slot.
     *
     * @param booking booking entity
     * @param now current timestamp
     * @return true when is active hold slot succeeds
     */
    boolean isActiveHoldSlot(BookingEntity booking, LocalDateTime now);

    /**
     * Checks whether locked cancelled slot.
     *
     * @param booking booking entity
     * @return true when is locked cancelled slot succeeds
     */
    boolean isLockedCancelledSlot(BookingEntity booking);

    /**
     * Executes matches admin hold session.
     *
     * @param booking booking entity
     * @param adminHoldSessionId admin hold session identifier
     * @return true when matches admin hold session succeeds
     */
    boolean matchesAdminHoldSession(BookingEntity booking, String adminHoldSessionId);

    /**
     * Executes matches admin hold session.
     *
     * @param slotHold slot-hold entity
     * @param adminHoldSessionId admin hold session identifier
     * @return true when matches admin hold session succeeds
     */
    boolean matchesAdminHoldSession(SlotHoldEntity slotHold, String adminHoldSessionId);

    /**
     * Checks whether admin panel hold.
     *
     * @param booking booking entity
     * @return true when is admin panel hold succeeds
     */
    boolean isAdminPanelHold(BookingEntity booking);

    /**
     * Checks whether admin panel hold.
     *
     * @param slotHold slot-hold entity
     * @return true when is admin panel hold succeeds
     */
    boolean isAdminPanelHold(SlotHoldEntity slotHold);

    /**
     * Executes to admin hold device id.
     *
     * @param adminHoldSessionId admin hold session identifier
     * @return result value
     */
    String toAdminHoldDeviceId(String adminHoldSessionId);
}

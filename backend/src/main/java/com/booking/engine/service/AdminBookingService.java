package com.booking.engine.service;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingCustomerLookupResponseDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.AdminBookingUpdateRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service contract for admin booking operations.
 * Defines admin booking related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface AdminBookingService {

    /**
     * Creates admin booking.
     *
     * @param request request payload
     * @return result value
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto createAdminBooking(AdminBookingCreateRequestDto request);

    /**
     * Creates admin booking.
     *
     * @param request request payload
     * @param adminHoldSessionId admin hold session identifier
     * @return result value
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto createAdminBooking(
            AdminBookingCreateRequestDto request,
            String adminHoldSessionId);

    /**
     * Holds admin slot.
     *
     * @param request request payload
     * @param adminHoldSessionId admin hold session identifier
     * @return result value
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto holdAdminSlot(BookingHoldRequestDto request, String adminHoldSessionId);

    /**
     * Refreshes admin hold.
     *
     * @param id identifier
     * @param adminHoldSessionId admin hold session identifier
     * @return result value
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto refreshAdminHold(UUID id, String adminHoldSessionId);

    /**
     * Releases admin hold.
     *
     * @param id identifier
     * @param adminHoldSessionId admin hold session identifier
     */
    @PreAuthorize("hasRole('ADMIN')")
    void releaseAdminHold(UUID id, String adminHoldSessionId);

    /**
     * Retrieves admin bookings.
     *
     * @param search search text
     * @return result value
     */
    @PreAuthorize("hasRole('ADMIN')")
    AdminBookingListResponseDto getAdminBookings(String search);

    /**
     * Finds latest customer by phone.
     *
     * @param phone phone value
     * @return optional result
     */
    @PreAuthorize("hasRole('ADMIN')")
    Optional<AdminBookingCustomerLookupResponseDto> findLatestCustomerByPhone(String phone);

    /**
     * Updates booking by admin.
     *
     * @param id identifier
     * @param request request payload
     * @return result value
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto updateBookingByAdmin(UUID id, AdminBookingUpdateRequestDto request);

    /**
     * Executes cancel booking by admin.
     *
     * @param id identifier
     * @return result value
     */
    @PreAuthorize("hasRole('ADMIN')")
    BookingResponseDto cancelBookingByAdmin(UUID id);
}

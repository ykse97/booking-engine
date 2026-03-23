package com.booking.engine.controller;

import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.BookingBlacklistEntryRequestDto;
import com.booking.engine.dto.BookingBlacklistEntryResponseDto;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST controller for appointment management and booking blacklist tools.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/admin/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminBookingReviewController {

    private final BookingService bookingService;
    private final BookingBlacklistService bookingBlacklistService;

    /**
     * Returns admin booking overview with optional search by customer contact data.
     *
     * @param search customer name / phone / email query
     * @return ordered booking list plus counters
     */
    @GetMapping
    public AdminBookingListResponseDto getAdminBookings(
            @RequestParam(required = false) String search) {
        log.info("HTTP GET /api/v1/admin/bookings search={}", search);
        return bookingService.getAdminBookings(search);
    }

    /**
     * Creates a confirmed booking from the admin panel without Stripe payment.
     *
     * @param request admin booking payload
     * @return created booking DTO
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> createAdminBooking(
            @Valid @RequestBody AdminBookingCreateRequestDto request) {
        log.info(
                "HTTP POST /api/v1/admin/bookings barberId={}, treatmentId={}, date={}, startTime={}",
                request.getBarberId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());
        return ResponseEntity.ok(bookingService.createAdminBooking(request));
    }

    /**
     * Cancels a booking from the admin panel.
     *
     * @param id booking identifier
     * @return updated booking DTO
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponseDto> cancelBooking(@PathVariable UUID id) {
        log.info("HTTP POST /api/v1/admin/bookings/{}/cancel", id);
        return ResponseEntity.ok(bookingService.cancelBookingByAdmin(id));
    }

    /**
     * Returns active blacklist entries that cannot be used for bookings.
     *
     * @return blacklist entries
     */
    @GetMapping("/blacklist")
    public List<BookingBlacklistEntryResponseDto> getBlacklistEntries() {
        log.info("HTTP GET /api/v1/admin/bookings/blacklist");
        return bookingBlacklistService.getActiveEntries();
    }

    /**
     * Creates a new blacklist entry.
     *
     * @param request blacklist payload
     * @return created entry
     */
    @PostMapping(value = "/blacklist", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingBlacklistEntryResponseDto> createBlacklistEntry(
            @Valid @RequestBody BookingBlacklistEntryRequestDto request) {
        log.info("HTTP POST /api/v1/admin/bookings/blacklist email={}, phone={}", request.getEmail(),
                request.getPhone());
        return ResponseEntity.ok(bookingBlacklistService.createEntry(request));
    }

    /**
     * Removes an active blacklist entry.
     *
     * @param id blacklist entry identifier
     * @return no content
     */
    @DeleteMapping("/blacklist/{id}")
    public ResponseEntity<Void> deleteBlacklistEntry(@PathVariable UUID id) {
        log.info("HTTP DELETE /api/v1/admin/bookings/blacklist/{}", id);
        bookingBlacklistService.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }
}

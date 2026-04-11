package com.booking.engine.controller;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingCheckoutValidationRequestDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.security.ClientIpResolver;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.service.PublicBookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST controller for booking operations.
 * Handles public booking creation, checkout, retrieval, and temporary hold cancellation.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/public/bookings", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private static final int MAX_DEVICE_ID_LENGTH = 128;

    private final PublicBookingService bookingService;
    private final ClientIpResolver clientIpResolver;
    private final SecurityAuditLogger securityAuditLogger;

    /**
     * Retrieves booking by ID.
     *
     * @param id the booking ID
     * @return booking details
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDto> getBookingById(@PathVariable UUID id) {
        log.info("event=http_request method=GET path=/api/v1/public/bookings/{id} bookingId={}", id);
        BookingResponseDto booking = bookingService.getBookingById(id);
        return ResponseEntity.ok(booking);
    }

    /**
     * Creates a new booking with immediate Stripe payment.
     *
     * @param request the booking request
     * @return created booking with 201 status
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody BookingRequestDto request) {

        log.info("event=http_request method=POST path=/api/v1/public/bookings employeeId={} treatmentId={} bookingDate={} startTime={}",
                request.getEmployeeId(), request.getTreatmentId(), request.getBookingDate(), request.getStartTime());
        BookingResponseDto created = bookingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Creates a temporary hold for a selected slot.
     *
     * @param request the slot hold request
     * @return pending booking with expiration timestamp
     */
    @PostMapping(value = "/hold", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> holdBookingSlot(
            @Valid @RequestBody BookingHoldRequestDto request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Booking-Device-Id", required = false)
            String clientDeviceId,
            HttpServletRequest httpServletRequest) {

        log.info("event=http_request method=POST path=/api/v1/public/bookings/hold employeeId={} treatmentId={} bookingDate={} startTime={}",
                request.getEmployeeId(), request.getTreatmentId(), request.getBookingDate(), request.getStartTime());
        BookingResponseDto heldBooking = bookingService.holdSlot(
                request,
                clientIpResolver.resolve(httpServletRequest),
                sanitizeClientDeviceId(clientDeviceId));
        return ResponseEntity.status(HttpStatus.CREATED).body(heldBooking);
    }

    /**
     * Validates a held booking before Stripe checkout is opened, so blocked
     * customers see the error immediately when pressing Confirm Booking.
     *
     * @param id booking identifier
     * @param request customer details to validate
     * @return 204 No Content when checkout may proceed
     */
    @PostMapping(value = "/{id}/checkout/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> validateHeldBookingCheckout(
            @PathVariable UUID id,
            @Valid @RequestBody BookingCheckoutValidationRequestDto request) {

        log.info("event=http_request method=POST path=/api/v1/public/bookings/{id}/checkout/validate bookingId={} customerEmailMask={}",
                id,
                maskEmailForLog(request.getCustomer() != null ? request.getCustomer().getEmail() : null));
        bookingService.validateHeldBookingCheckout(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Prepares Stripe checkout for a previously held booking.
     *
     * @param id booking identifier
     * @param request checkout payload with customer details
     * @return PaymentIntent client secret and status
     */
    @PostMapping(value = "/{id}/checkout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingCheckoutSessionResponseDto> prepareHeldBookingCheckout(
            @PathVariable UUID id,
            @Valid @RequestBody BookingCheckoutSessionRequestDto request) {

        log.info(
                "event=http_request method=POST path=/api/v1/public/bookings/{id}/checkout bookingId={} customerEmailMask={} confirmationTokenPresent={}",
                id,
                maskEmailForLog(request.getCustomer() != null ? request.getCustomer().getEmail() : null),
                request.getConfirmationTokenId() != null && !request.getConfirmationTokenId().isBlank());
        BookingCheckoutSessionResponseDto checkoutSession = bookingService.prepareHeldBookingCheckout(id, request);
        return ResponseEntity.ok(checkoutSession);
    }

    /**
     * Finalizes a previously held booking after Stripe payment.
     * The booking is confirmed immediately once the backend verifies Stripe
     * reported a successful PaymentIntent, while the webhook remains a fallback
     * synchronization channel.
     *
     * @param id booking identifier
     * @param request booking confirmation payload
     * @return latest booking state after checkout confirmation
     */
    @PostMapping(value = "/{id}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BookingResponseDto> confirmHeldBooking(
            @PathVariable UUID id,
            @Valid @RequestBody BookingConfirmationRequestDto request) {

        log.info("event=http_request method=POST path=/api/v1/public/bookings/{id}/confirm bookingId={}", id);
        BookingResponseDto confirmed = bookingService.confirmHeldBooking(id, request);
        return ResponseEntity.ok(confirmed);
    }

    /**
     * Cancels an unpaid temporary booking hold.
     *
     * @param id the booking ID to cancel
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable UUID id) {
        log.info("event=http_request method=DELETE path=/api/v1/public/bookings/{id} bookingId={}", id);
        bookingService.cancelBooking(id);
        return ResponseEntity.noContent().build();
    }

    /*
     * Cleans the optional booking device identifier and caps its length so
     * untrusted clients cannot send oversized values into persistence.
     *
     * @param clientDeviceId raw device id header value
     * @return sanitized device id or {@code null} when blank
     */
    private String sanitizeClientDeviceId(String clientDeviceId) {
        String cleaned = cleanOptionalValue(clientDeviceId);
        if (cleaned == null) {
            return null;
        }

        return cleaned.length() > MAX_DEVICE_ID_LENGTH
                ? cleaned.substring(0, MAX_DEVICE_ID_LENGTH)
                : cleaned;
    }

    /*
     * Trims a request-derived value and discards blank or placeholder input such
     * as {@code unknown} so downstream helpers work with meaningful client data.
     *
     * @param value raw request value
     * @return cleaned value or {@code null} when invalid
     */
    private String cleanOptionalValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isBlank() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }

        return trimmed;
    }

    private String maskEmailForLog(String email) {
        return securityAuditLogger.maskEmail(cleanOptionalValue(email));
    }
}

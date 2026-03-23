package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.UUID;

import com.booking.engine.entity.BookingStatus;

/**
 * Response DTO for booking data.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDto {

    // ---------------------- IDs ----------------------

    private UUID id;
    private UUID barberId;
    private UUID treatmentId;
    private String barberName;
    private String treatmentName;

    // ---------------------- Slot ----------------------

    private LocalDate bookingDate;
    private LocalTime startTime;
    private LocalTime endTime;

    // ---------------------- Customer ----------------------
    // Customer fields (flat for easy access in UI tables/lists)
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    // ---------------------- Status ----------------------

    private BookingStatus status;
    private LocalDateTime expiresAt;
    private String stripePaymentIntentId;
    private String stripePaymentStatus;
    private BigDecimal holdAmount;
    private LocalDateTime paymentCapturedAt;
    private LocalDateTime paymentReleasedAt;

    // ---------------------- Metadata ----------------------

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

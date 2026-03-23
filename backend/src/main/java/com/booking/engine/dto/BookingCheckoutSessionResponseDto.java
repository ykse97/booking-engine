package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stripe checkout payload used by the booking modal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCheckoutSessionResponseDto {

    private String paymentIntentId;
    private String clientSecret;
    private String paymentStatus;
}

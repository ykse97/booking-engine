package com.booking.engine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for finalizing a previously held booking after Stripe payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmationRequestDto {

    @NotBlank(message = "Stripe payment intent ID is required")
    private String paymentIntentId;
}

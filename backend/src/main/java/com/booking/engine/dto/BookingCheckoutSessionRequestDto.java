package com.booking.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for preparing Stripe checkout on top of an existing held slot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCheckoutSessionRequestDto {

    @Valid
    @NotNull(message = "Customer details are required")
    private BookingRequestDto.CustomerDetailsDto customer;

    @NotBlank(message = "Stripe confirmation token is required")
    private String confirmationTokenId;
}

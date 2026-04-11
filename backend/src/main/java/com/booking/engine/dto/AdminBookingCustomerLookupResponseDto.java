package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight customer lookup payload for admin phone booking autofill.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingCustomerLookupResponseDto {

    private String customerName;
    private String customerPhone;
    private String customerEmail;
}

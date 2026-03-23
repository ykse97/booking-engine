package com.booking.engine.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for booking blacklist entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingBlacklistEntryRequestDto {

    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String email;

    @Size(max = 50, message = "Phone cannot exceed 50 characters")
    private String phone;

    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}

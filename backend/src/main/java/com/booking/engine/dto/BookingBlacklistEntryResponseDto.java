package com.booking.engine.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for active booking blacklist entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingBlacklistEntryResponseDto {

    private UUID id;
    private String email;
    private String phone;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

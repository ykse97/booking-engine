package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for hair salon data.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HairSalonResponseDto {

    private UUID id;
    private String name;
    private String description;
    private String email;
    private String phone;
    private String address;
    private List<HairSalonHoursResponseDto> workingHours;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
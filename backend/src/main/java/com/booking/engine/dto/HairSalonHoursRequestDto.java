package com.booking.engine.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating one working day configuration.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HairSalonHoursRequestDto {

    /** Indicates whether the day is working or non-working. */
    @NotNull
    private Boolean workingDay;

    /** Opening time (required when {@code workingDay=true}). */
    private LocalTime openTime;

    /** Closing time (required when {@code workingDay=true}). */
    private LocalTime closeTime;
}

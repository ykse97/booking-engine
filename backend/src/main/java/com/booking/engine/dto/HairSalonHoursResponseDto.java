package com.booking.engine.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO representing configured working hours for one day.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Data
@Builder
public class HairSalonHoursResponseDto {

    /** Day of week entry. */
    private DayOfWeek dayOfWeek;

    /** Indicates whether the salon is open on this day. */
    private boolean workingDay;

    /** Opening time for working day. */
    private LocalTime openTime;

    /** Closing time for working day. */
    private LocalTime closeTime;
}

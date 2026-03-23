package com.booking.engine.service;

import com.booking.engine.dto.BarberSchedulePeriodRequestDto;
import com.booking.engine.dto.BarberSchedulePeriodResponseDto;
import com.booking.engine.dto.BarberScheduleRequestDto;
import com.booking.engine.dto.BarberScheduleResponseDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for per-date barber schedule management.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
public interface BarberScheduleService {

    /**
     * Retrieves barber schedule for a date range (inclusive).
     *
     * @param barberId barber identifier
     * @param from     start date (inclusive), may be null
     * @param to       end date (inclusive), may be null
     * @return list of schedule entries
     */
    List<BarberScheduleResponseDto> getSchedule(UUID barberId, LocalDate from, LocalDate to);

    /**
     * Creates or updates schedule data for a single day.
     *
     * @param barberId barber identifier
     * @param request  schedule payload
     */
    void upsertDay(UUID barberId, BarberScheduleRequestDto request);

    /**
     * Returns the latest saved barber schedule period form state.
     *
     * @return persisted form state
     */
    BarberSchedulePeriodResponseDto getPeriodSettings();

    /**
     * Creates or updates schedule data for every matching day in a date period.
     *
     * @param request bulk schedule payload
     * @return persisted form state after update
     */
    BarberSchedulePeriodResponseDto upsertPeriod(BarberSchedulePeriodRequestDto request);
}

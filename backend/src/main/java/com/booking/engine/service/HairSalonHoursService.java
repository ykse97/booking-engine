package com.booking.engine.service;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

import com.booking.engine.dto.HairSalonHoursRequestDto;
import com.booking.engine.dto.HairSalonHoursResponseDto;

/**
 * Service contract for hair salon weekly working hours.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public interface HairSalonHoursService {

    /**
     * Retrieves full weekly schedule for a salon.
     *
     * @param hairSalonId salon identifier
     * @return list of working day DTOs
     */
    List<HairSalonHoursResponseDto> getWorkingHours(UUID hairSalonId);

    /**
     * Updates schedule for one day.
     *
     * @param hairSalonId salon identifier
     * @param dayOfWeek   target day
     * @param request     update payload
     */
    void updateWorkingDay(UUID hairSalonId, DayOfWeek dayOfWeek, HairSalonHoursRequestDto request);
}

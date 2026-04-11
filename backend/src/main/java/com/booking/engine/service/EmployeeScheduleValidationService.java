package com.booking.engine.service;

import com.booking.engine.dto.EmployeeSchedulePeriodDayRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Service contract for employee schedule validation operations.
 * Defines employee schedule validation related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface EmployeeScheduleValidationService {

    /**
     * Maps weekly configs.
     *
     * @param days day configuration list
     * @return result map
     */
    Map<DayOfWeek, EmployeeSchedulePeriodDayRequestDto> mapWeeklyConfigs(
            List<EmployeeSchedulePeriodDayRequestDto> days);

    /**
     * Validates period request.
     *
     * @param request request payload
     */
    void validatePeriodRequest(EmployeeSchedulePeriodRequestDto request);

    /**
     * Validates working hours.
     *
     * @param workingDay working-day flag
     * @param openTime open time
     * @param closeTime close time
     * @param breakStartTime break start time
     * @param breakEndTime break end time
     */
    void validateWorkingHours(
            Boolean workingDay,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime);
}

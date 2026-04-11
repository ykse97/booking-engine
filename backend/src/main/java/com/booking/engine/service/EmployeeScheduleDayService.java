package com.booking.engine.service;

import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for employee schedule day operations.
 * Defines employee schedule day related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface EmployeeScheduleDayService {

    /**
     * Retrieves schedule.
     *
     * @param employeeId employee identifier
     * @param from start value
     * @param to end value
     * @return result list
     */
    List<EmployeeScheduleResponseDto> getSchedule(UUID employeeId, LocalDate from, LocalDate to);

    /**
     * Executes upsert day.
     *
     * @param employeeId employee identifier
     * @param request request payload
     */
    void upsertDay(UUID employeeId, EmployeeScheduleRequestDto request);
}

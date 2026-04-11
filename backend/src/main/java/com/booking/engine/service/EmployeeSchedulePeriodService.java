package com.booking.engine.service;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;

/**
 * Service contract for employee schedule period operations.
 * Defines employee schedule period related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface EmployeeSchedulePeriodService {

    /**
     * Retrieves period settings.
     *
     * @return result value
     */
    EmployeeSchedulePeriodResponseDto getPeriodSettings();

    /**
     * Executes upsert period.
     *
     * @param request request payload
     * @return result value
     */
    EmployeeSchedulePeriodResponseDto upsertPeriod(EmployeeSchedulePeriodRequestDto request);
}

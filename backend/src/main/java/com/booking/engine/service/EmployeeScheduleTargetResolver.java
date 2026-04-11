package com.booking.engine.service;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.EmployeeSchedulePeriodSettingsEntity;
import java.util.Map;
import java.util.UUID;

/**
 * Service contract for employee schedule target resolver operations.
 * Defines employee schedule target resolver related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface EmployeeScheduleTargetResolver {

    /**
     * Resolves employee or throw.
     *
     * @param employeeId employee identifier
     * @return result value
     */
    EmployeeEntity resolveEmployeeOrThrow(UUID employeeId);

    /**
     * Resolves target employees.
     *
     * @param request request payload
     * @param settings settings entity
     * @return result map
     */
    Map<UUID, EmployeeEntity> resolveTargetEmployees(
            EmployeeSchedulePeriodRequestDto request,
            EmployeeSchedulePeriodSettingsEntity settings);
}

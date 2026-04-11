package com.booking.engine.service;

import com.booking.engine.entity.EmployeeEntity;

/**
 * Service contract for employee schedule seed operations.
 * Defines employee schedule seed related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface EmployeeScheduleSeedService {

    /**
     * Executes seed upcoming schedule if bookable.
     *
     * @param employee employee entity
     */
    void seedUpcomingScheduleIfBookable(EmployeeEntity employee);
}

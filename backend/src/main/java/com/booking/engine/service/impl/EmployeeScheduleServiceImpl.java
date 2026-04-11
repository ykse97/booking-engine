package com.booking.engine.service.impl;

import com.booking.engine.dto.EmployeeSchedulePeriodRequestDto;
import com.booking.engine.dto.EmployeeSchedulePeriodResponseDto;
import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.service.EmployeeScheduleDayService;
import com.booking.engine.service.EmployeeSchedulePeriodService;
import com.booking.engine.service.EmployeeScheduleService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EmployeeScheduleService}.
 * Provides employee schedule related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeScheduleServiceImpl implements EmployeeScheduleService {
    // ---------------------- Services ----------------------

    private final EmployeeScheduleDayService employeeScheduleDayService;

    private final EmployeeSchedulePeriodService employeeSchedulePeriodService;
    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EmployeeScheduleResponseDto> getSchedule(UUID employeeId, LocalDate from, LocalDate to) {
        return employeeScheduleDayService.getSchedule(employeeId, from, to);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void upsertDay(UUID employeeId, EmployeeScheduleRequestDto request) {
        log.debug("event=employee_schedule_upsert_day action=start employeeId={} workingDate={} workingDay={}",
                employeeId, request.getWorkingDate(), request.getWorkingDay());

        employeeScheduleDayService.upsertDay(employeeId, request);
        log.info("event=employee_schedule_upsert_day action=success employeeId={} workingDate={} workingDay={}",
                employeeId,
                request.getWorkingDate(),
                request.getWorkingDay());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EmployeeSchedulePeriodResponseDto getPeriodSettings() {
        return employeeSchedulePeriodService.getPeriodSettings();
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public EmployeeSchedulePeriodResponseDto upsertPeriod(EmployeeSchedulePeriodRequestDto request) {
        log.debug(
                "event=employee_schedule_upsert_period action=start startDate={} endDate={} employeeId={} allEmployees={}",
                request.getStartDate(),
                request.getEndDate(),
                request.getEmployeeId(),
                request.getApplyToAllEmployees());

        EmployeeSchedulePeriodResponseDto response = employeeSchedulePeriodService.upsertPeriod(request);
        log.info(
                "event=employee_schedule_upsert_period action=success startDate={} endDate={} employeeId={} allEmployees={} dayCount={}",
                response.getStartDate(),
                response.getEndDate(),
                response.getEmployeeId(),
                response.getApplyToAllEmployees(),
                response.getDays() != null ? response.getDays().size() : 0);
        return response;
    }
}

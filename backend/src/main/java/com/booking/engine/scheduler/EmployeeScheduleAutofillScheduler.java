package com.booking.engine.scheduler;

import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.HairSalonHoursEntity;
import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.HairSalonRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduler that precreates employee daily schedules from salon defaults.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeScheduleAutofillScheduler {

    private static final int DAYS_AHEAD = 7;

    private final EmployeeRepository employeeRepository;
    private final EmployeeDailyScheduleRepository employeeDailyScheduleRepository;
    private final HairSalonRepository hairSalonRepository;
    private final HairSalonProperties hairSalonProperties;

    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void ensureNext7DaysSchedules() {
        LocalDate today = LocalDate.now();
        long deletedPastSchedules = employeeDailyScheduleRepository.deleteByWorkingDateBefore(today);
        if (deletedPastSchedules > 0) {
            log.info("Deleted {} employee daily schedules older than {}", deletedPastSchedules, today);
        }

        List<EmployeeEntity> eligibleEmployees = employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc();
        if (eligibleEmployees.isEmpty()) {
            return;
        }

        LocalDate endDate = today.plusDays(DAYS_AHEAD - 1L);

        HairSalonEntity hairSalon = hairSalonRepository.findById(hairSalonProperties.getId()).orElse(null);
        if (hairSalon == null) {
            log.warn("Skipping schedule autofill because hair salon id={} was not found", hairSalonProperties.getId());
            return;
        }

        Map<java.time.DayOfWeek, HairSalonHoursEntity> salonHoursByDay = new HashMap<>();
        for (HairSalonHoursEntity hours : hairSalon.getWorkingHours()) {
            salonHoursByDay.put(hours.getDayOfWeek(), hours);
        }

        List<UUID> employeeIds = eligibleEmployees.stream().map(EmployeeEntity::getId).toList();
        Map<String, EmployeeDailyScheduleEntity> existingSchedules = employeeDailyScheduleRepository
                .findByEmployeeIdInAndWorkingDateBetween(employeeIds, today, endDate)
                .stream()
                .collect(HashMap::new, (map, schedule) -> map.put(
                        key(schedule.getEmployee().getId(), schedule.getWorkingDate()), schedule), HashMap::putAll);

        List<EmployeeDailyScheduleEntity> toCreate = new ArrayList<>();

        for (EmployeeEntity employee : eligibleEmployees) {
            if (!Boolean.TRUE.equals(employee.getBookable())) {
                continue;
            }

            for (LocalDate date = today; !date.isAfter(endDate); date = date.plusDays(1)) {
                String key = key(employee.getId(), date);
                if (existingSchedules.containsKey(key)) {
                    continue;
                }

                HairSalonHoursEntity defaultHours = salonHoursByDay.get(date.getDayOfWeek());
                EmployeeDailyScheduleEntity schedule = new EmployeeDailyScheduleEntity();
                schedule.setEmployee(employee);
                schedule.setWorkingDate(date);

                if (defaultHours == null || !defaultHours.isWorkingDay()) {
                    schedule.setWorkingDay(false);
                    schedule.setOpenTime(null);
                    schedule.setCloseTime(null);
                } else {
                    schedule.setWorkingDay(true);
                    schedule.setOpenTime(defaultHours.getOpenTime());
                    schedule.setCloseTime(defaultHours.getCloseTime());
                }

                schedule.setBreakStartTime(null);
                schedule.setBreakEndTime(null);
                toCreate.add(schedule);
            }
        }

        if (!toCreate.isEmpty()) {
            employeeDailyScheduleRepository.saveAll(toCreate);
            log.info("Autofilled {} employee daily schedules for next {} days", toCreate.size(), DAYS_AHEAD);
        }
    }

    private String key(UUID employeeId, LocalDate date) {
        return employeeId + "|" + date;
    }
}

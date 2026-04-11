package com.booking.engine.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.HairSalonHoursEntity;
import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.EmployeeDailyScheduleRepository;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.repository.HairSalonRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeScheduleAutofillSchedulerTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeDailyScheduleRepository employeeDailyScheduleRepository;

    @Mock
    private HairSalonRepository hairSalonRepository;

    private HairSalonProperties hairSalonProperties;
    private EmployeeScheduleAutofillScheduler scheduler;

    @BeforeEach
    void setUp() {
        hairSalonProperties = new HairSalonProperties();
        hairSalonProperties.setId(UUID.randomUUID());
        scheduler = new EmployeeScheduleAutofillScheduler(
                employeeRepository,
                employeeDailyScheduleRepository,
                hairSalonRepository,
                hairSalonProperties);
    }

    @Test
    void ensureNext7DaysSchedulesCreatesMissingDatesForNextWeekUsingSalonHours() {
        EmployeeEntity employee = employee(true);
        HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDate today = LocalDate.now();

        when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(today)).thenReturn(0L);
        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc()).thenReturn(List.of(employee));
        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
        when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(employee.getId()), today, today.plusDays(6)))
                .thenReturn(List.of());

        scheduler.ensureNext7DaysSchedules();

        ArgumentCaptor<Iterable<EmployeeDailyScheduleEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(employeeDailyScheduleRepository).saveAll(captor.capture());
        List<EmployeeDailyScheduleEntity> saved = toList(captor.getValue());

        assertEquals(7, saved.size());
        verify(employeeDailyScheduleRepository).deleteByWorkingDateBefore(today);
        assertNotNull(saved.get(0).getWorkingDate());
        assertEquals(LocalTime.of(9, 0), saved.get(0).getOpenTime());
        assertEquals(LocalTime.of(17, 0), saved.get(0).getCloseTime());
    }

    @Test
    void ensureNext7DaysSchedulesSkipsNonBookableEmployees() {
        EmployeeEntity nonBookable = employee(false);
        HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDate today = LocalDate.now();

        when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(today)).thenReturn(0L);
        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc()).thenReturn(List.of(nonBookable));
        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
        when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(nonBookable.getId()), today, today.plusDays(6)))
                .thenReturn(List.of());

        scheduler.ensureNext7DaysSchedules();

        verify(employeeDailyScheduleRepository, never()).saveAll(any());
    }

    @Test
    void ensureNext7DaysSchedulesDoesNotOverwriteExistingDatesAndAvoidsDuplicates() {
        EmployeeEntity employee = employee(true);
        HairSalonEntity salon = salonWithWeekdayHours(true, LocalTime.of(9, 0), LocalTime.of(17, 0));
        LocalDate today = LocalDate.now();
        LocalDate existingDate = today.plusDays(2);

        EmployeeDailyScheduleEntity existing = new EmployeeDailyScheduleEntity();
        existing.setEmployee(employee);
        existing.setWorkingDate(existingDate);
        existing.setWorkingDay(true);
        existing.setOpenTime(LocalTime.of(12, 0));
        existing.setCloseTime(LocalTime.of(18, 0));

        when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(today)).thenReturn(0L);
        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc()).thenReturn(List.of(employee));
        when(hairSalonRepository.findById(hairSalonProperties.getId())).thenReturn(Optional.of(salon));
        when(employeeDailyScheduleRepository.findByEmployeeIdInAndWorkingDateBetween(
                List.of(employee.getId()), today, today.plusDays(6)))
                .thenReturn(List.of(existing));

        scheduler.ensureNext7DaysSchedules();

        ArgumentCaptor<Iterable<EmployeeDailyScheduleEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(employeeDailyScheduleRepository).saveAll(captor.capture());
        List<EmployeeDailyScheduleEntity> saved = toList(captor.getValue());

        assertEquals(6, saved.size());
        assertFalse(saved.stream().anyMatch(item -> existingDate.equals(item.getWorkingDate())));
        assertEquals(1, saved.stream().filter(item -> today.equals(item.getWorkingDate())).count());
        assertTrue(saved.stream().noneMatch(item -> item.getOpenTime() != null && item.getOpenTime().equals(LocalTime.of(12, 0))));
    }

    @Test
    void ensureNext7DaysSchedulesDeletesPastSchedulesEvenWhenNoEligibleEmployees() {
        LocalDate today = LocalDate.now();

        when(employeeDailyScheduleRepository.deleteByWorkingDateBefore(today)).thenReturn(4L);
        when(employeeRepository.findAllByActiveTrueAndBookableTrueOrderByDisplayOrderAsc()).thenReturn(List.of());

        scheduler.ensureNext7DaysSchedules();

        verify(employeeDailyScheduleRepository).deleteByWorkingDateBefore(today);
        verify(employeeDailyScheduleRepository, never()).saveAll(any());
    }

    private EmployeeEntity employee(boolean bookable) {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setId(UUID.randomUUID());
        employee.setActive(true);
        employee.setBookable(bookable);
        return employee;
    }

    private HairSalonEntity salonWithWeekdayHours(boolean workingDay, LocalTime open, LocalTime close) {
        HairSalonEntity salon = new HairSalonEntity();
        salon.setId(hairSalonProperties.getId());
        salon.setWorkingHours(List.of(
                dayHours(DayOfWeek.MONDAY, workingDay, open, close),
                dayHours(DayOfWeek.TUESDAY, workingDay, open, close),
                dayHours(DayOfWeek.WEDNESDAY, workingDay, open, close),
                dayHours(DayOfWeek.THURSDAY, workingDay, open, close),
                dayHours(DayOfWeek.FRIDAY, workingDay, open, close),
                dayHours(DayOfWeek.SATURDAY, workingDay, open, close),
                dayHours(DayOfWeek.SUNDAY, workingDay, open, close)));
        return salon;
    }

    private HairSalonHoursEntity dayHours(DayOfWeek day, boolean workingDay, LocalTime open, LocalTime close) {
        HairSalonHoursEntity entity = new HairSalonHoursEntity();
        entity.setDayOfWeek(day);
        entity.setWorkingDay(workingDay);
        entity.setOpenTime(workingDay ? open : null);
        entity.setCloseTime(workingDay ? close : null);
        return entity;
    }

    private List<EmployeeDailyScheduleEntity> toList(Iterable<EmployeeDailyScheduleEntity> iterable) {
        return iterable instanceof List<EmployeeDailyScheduleEntity> list ? list : java.util.stream.StreamSupport
                .stream(iterable.spliterator(), false)
                .toList();
    }
}

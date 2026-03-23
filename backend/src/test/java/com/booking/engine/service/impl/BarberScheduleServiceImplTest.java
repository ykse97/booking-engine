package com.booking.engine.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.dto.BarberSchedulePeriodDayRequestDto;
import com.booking.engine.dto.BarberSchedulePeriodRequestDto;
import com.booking.engine.dto.BarberScheduleRequestDto;
import com.booking.engine.entity.BarberDailyScheduleEntity;
import com.booking.engine.entity.BarberEntity;
import com.booking.engine.entity.BarberSchedulePeriodDaySettingsEntity;
import com.booking.engine.entity.BarberSchedulePeriodSettingsEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.BarberScheduleMapper;
import com.booking.engine.repository.BarberDailyScheduleRepository;
import com.booking.engine.repository.BarberRepository;
import com.booking.engine.repository.BarberSchedulePeriodDaySettingsRepository;
import com.booking.engine.repository.BarberSchedulePeriodSettingsRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BarberScheduleServiceImplTest {

    private static final int PERIOD_SETTINGS_SINGLETON_ID = 1;

    private UUID barberId;
    private LocalDate today;

    @BeforeEach
    void setup() {
        barberId = UUID.randomUUID();
        today = LocalDate.now();
    }

    @Test
    void upsertCreatesNewWhenNotExists() {
        BarberDailyScheduleRepository scheduleRepository = mock(BarberDailyScheduleRepository.class);
        BarberRepository barberRepository = mock(BarberRepository.class);
        BarberScheduleMapper mapper = mock(BarberScheduleMapper.class);
        BarberSchedulePeriodSettingsRepository periodSettingsRepository = mock(BarberSchedulePeriodSettingsRepository.class);
        BarberSchedulePeriodDaySettingsRepository periodDaySettingsRepository = mock(BarberSchedulePeriodDaySettingsRepository.class);
        BarberScheduleServiceImpl service = new BarberScheduleServiceImpl(
                scheduleRepository,
                barberRepository,
                periodSettingsRepository,
                periodDaySettingsRepository,
                mapper);

        BarberScheduleRequestDto req = BarberScheduleRequestDto.builder()
                .workingDate(today)
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .breakStartTime(LocalTime.of(13, 0))
                .breakEndTime(LocalTime.of(14, 0))
                .build();

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(new BarberEntity()));
        when(scheduleRepository.findByBarberIdAndWorkingDate(barberId, today)).thenReturn(Optional.empty());

        BarberDailyScheduleEntity mapped = new BarberDailyScheduleEntity();
        when(mapper.toEntity(req)).thenReturn(mapped);
        when(barberRepository.getReferenceById(barberId)).thenReturn(new BarberEntity());

        service.upsertDay(barberId, req);

        verify(scheduleRepository).save(mapped);
    }

    @Test
    void upsertUpdatesExisting() {
        BarberDailyScheduleRepository scheduleRepository = mock(BarberDailyScheduleRepository.class);
        BarberRepository barberRepository = mock(BarberRepository.class);
        BarberScheduleMapper mapper = mock(BarberScheduleMapper.class);
        BarberSchedulePeriodSettingsRepository periodSettingsRepository = mock(BarberSchedulePeriodSettingsRepository.class);
        BarberSchedulePeriodDaySettingsRepository periodDaySettingsRepository = mock(BarberSchedulePeriodDaySettingsRepository.class);
        BarberScheduleServiceImpl service = new BarberScheduleServiceImpl(
                scheduleRepository,
                barberRepository,
                periodSettingsRepository,
                periodDaySettingsRepository,
                mapper);

        BarberScheduleRequestDto req = BarberScheduleRequestDto.builder()
                .workingDate(today)
                .workingDay(false)
                .build();

        BarberDailyScheduleEntity existing = new BarberDailyScheduleEntity();
        existing.setWorkingDate(today);
        existing.setWorkingDay(true);

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(new BarberEntity()));
        when(scheduleRepository.findByBarberIdAndWorkingDate(barberId, today)).thenReturn(Optional.of(existing));

        service.upsertDay(barberId, req);

        verify(scheduleRepository).save(existing);
        assertEquals(false, existing.isWorkingDay());
        assertNull(existing.getOpenTime());
        assertNull(existing.getBreakStartTime());
    }

    @Test
    void upsertThrowsWhenBarberMissing() {
        BarberDailyScheduleRepository scheduleRepository = mock(BarberDailyScheduleRepository.class);
        BarberRepository barberRepository = mock(BarberRepository.class);
        BarberScheduleMapper mapper = mock(BarberScheduleMapper.class);
        BarberSchedulePeriodSettingsRepository periodSettingsRepository = mock(BarberSchedulePeriodSettingsRepository.class);
        BarberSchedulePeriodDaySettingsRepository periodDaySettingsRepository = mock(BarberSchedulePeriodDaySettingsRepository.class);
        BarberScheduleServiceImpl service = new BarberScheduleServiceImpl(
                scheduleRepository,
                barberRepository,
                periodSettingsRepository,
                periodDaySettingsRepository,
                mapper);

        BarberScheduleRequestDto req = BarberScheduleRequestDto.builder()
                .workingDate(today)
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .breakStartTime(LocalTime.of(13, 0))
                .breakEndTime(LocalTime.of(14, 0))
                .build();

        when(barberRepository.findById(barberId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.upsertDay(barberId, req));
    }

    @Test
    void upsertThrowsWhenWorkingDayBreakIsMissing() {
        BarberDailyScheduleRepository scheduleRepository = mock(BarberDailyScheduleRepository.class);
        BarberRepository barberRepository = mock(BarberRepository.class);
        BarberScheduleMapper mapper = mock(BarberScheduleMapper.class);
        BarberSchedulePeriodSettingsRepository periodSettingsRepository = mock(BarberSchedulePeriodSettingsRepository.class);
        BarberSchedulePeriodDaySettingsRepository periodDaySettingsRepository = mock(BarberSchedulePeriodDaySettingsRepository.class);
        BarberScheduleServiceImpl service = new BarberScheduleServiceImpl(
                scheduleRepository,
                barberRepository,
                periodSettingsRepository,
                periodDaySettingsRepository,
                mapper);

        BarberScheduleRequestDto req = BarberScheduleRequestDto.builder()
                .workingDate(today)
                .workingDay(true)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.upsertDay(barberId, req));
    }

    @Test
    void upsertPeriodForSpecificBarberSavesEachDateInRange() {
        BarberDailyScheduleRepository scheduleRepository = mock(BarberDailyScheduleRepository.class);
        BarberRepository barberRepository = mock(BarberRepository.class);
        BarberScheduleMapper mapper = mock(BarberScheduleMapper.class);
        BarberSchedulePeriodSettingsRepository periodSettingsRepository = mock(BarberSchedulePeriodSettingsRepository.class);
        BarberSchedulePeriodDaySettingsRepository periodDaySettingsRepository = mock(BarberSchedulePeriodDaySettingsRepository.class);
        BarberScheduleServiceImpl service = new BarberScheduleServiceImpl(
                scheduleRepository,
                barberRepository,
                periodSettingsRepository,
                periodDaySettingsRepository,
                mapper);

        BarberEntity barber = new BarberEntity();
        barber.setId(barberId);

        LocalDate start = LocalDate.of(2026, 4, 6);
        LocalDate end = LocalDate.of(2026, 4, 8);

        BarberSchedulePeriodRequestDto request = BarberSchedulePeriodRequestDto.builder()
                .startDate(start)
                .endDate(end)
                .barberId(barberId)
                .applyToAllBarbers(false)
                .days(weeklyPeriodConfig())
                .build();

        when(barberRepository.findById(barberId)).thenReturn(Optional.of(barber));
        when(scheduleRepository.findByBarberIdInAndWorkingDateBetween(List.of(barberId), start, end))
                .thenReturn(List.of());
        when(periodSettingsRepository.findById(PERIOD_SETTINGS_SINGLETON_ID))
                .thenReturn(Optional.of(periodSettingsEntity()));
        when(periodDaySettingsRepository.findAll()).thenReturn(periodDaySettingsEntities());

        service.upsertPeriod(request);

        ArgumentCaptor<Iterable<BarberDailyScheduleEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(scheduleRepository).saveAll(captor.capture());

        List<BarberDailyScheduleEntity> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();
        assertEquals(3, saved.size());
        assertEquals(true, saved.get(0).isWorkingDay());
        assertEquals(LocalTime.of(9, 0), saved.get(0).getOpenTime());
        assertEquals(false, saved.get(1).isWorkingDay());
        assertNull(saved.get(1).getOpenTime());
        assertEquals(false, saved.get(2).isWorkingDay());
    }

    @Test
    void upsertPeriodForAllBarbersSavesRowsForEachBarber() {
        BarberDailyScheduleRepository scheduleRepository = mock(BarberDailyScheduleRepository.class);
        BarberRepository barberRepository = mock(BarberRepository.class);
        BarberScheduleMapper mapper = mock(BarberScheduleMapper.class);
        BarberSchedulePeriodSettingsRepository periodSettingsRepository = mock(BarberSchedulePeriodSettingsRepository.class);
        BarberSchedulePeriodDaySettingsRepository periodDaySettingsRepository = mock(BarberSchedulePeriodDaySettingsRepository.class);
        BarberScheduleServiceImpl service = new BarberScheduleServiceImpl(
                scheduleRepository,
                barberRepository,
                periodSettingsRepository,
                periodDaySettingsRepository,
                mapper);

        BarberEntity firstBarber = new BarberEntity();
        firstBarber.setId(UUID.randomUUID());
        BarberEntity secondBarber = new BarberEntity();
        secondBarber.setId(UUID.randomUUID());

        LocalDate date = LocalDate.of(2026, 4, 6);

        BarberSchedulePeriodRequestDto request = BarberSchedulePeriodRequestDto.builder()
                .startDate(date)
                .endDate(date)
                .applyToAllBarbers(true)
                .days(weeklyPeriodConfig())
                .build();

        when(barberRepository.findAllByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(firstBarber, secondBarber));
        when(scheduleRepository.findByBarberIdInAndWorkingDateBetween(
                List.of(firstBarber.getId(), secondBarber.getId()), date, date))
                .thenReturn(List.of());
        when(periodSettingsRepository.findById(PERIOD_SETTINGS_SINGLETON_ID))
                .thenReturn(Optional.of(periodSettingsEntity()));
        when(periodDaySettingsRepository.findAll()).thenReturn(periodDaySettingsEntities());

        service.upsertPeriod(request);

        ArgumentCaptor<Iterable<BarberDailyScheduleEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(scheduleRepository).saveAll(captor.capture());

        List<BarberDailyScheduleEntity> saved = StreamSupport.stream(captor.getValue().spliterator(), false).toList();
        assertEquals(2, saved.size());
        assertEquals(true, saved.get(0).isWorkingDay());
        assertEquals(true, saved.get(1).isWorkingDay());
    }

    @Test
    void upsertPeriodThrowsWhenDatesAreInvalid() {
        BarberDailyScheduleRepository scheduleRepository = mock(BarberDailyScheduleRepository.class);
        BarberRepository barberRepository = mock(BarberRepository.class);
        BarberScheduleMapper mapper = mock(BarberScheduleMapper.class);
        BarberSchedulePeriodSettingsRepository periodSettingsRepository = mock(BarberSchedulePeriodSettingsRepository.class);
        BarberSchedulePeriodDaySettingsRepository periodDaySettingsRepository = mock(BarberSchedulePeriodDaySettingsRepository.class);
        BarberScheduleServiceImpl service = new BarberScheduleServiceImpl(
                scheduleRepository,
                barberRepository,
                periodSettingsRepository,
                periodDaySettingsRepository,
                mapper);

        BarberSchedulePeriodRequestDto request = BarberSchedulePeriodRequestDto.builder()
                .startDate(LocalDate.of(2026, 4, 10))
                .endDate(LocalDate.of(2026, 4, 6))
                .barberId(barberId)
                .applyToAllBarbers(false)
                .days(weeklyPeriodConfig())
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.upsertPeriod(request));
    }

    private List<BarberSchedulePeriodDayRequestDto> weeklyPeriodConfig() {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> {
                    boolean workingDay = day == DayOfWeek.MONDAY;
                    return BarberSchedulePeriodDayRequestDto.builder()
                            .dayOfWeek(day)
                            .workingDay(workingDay)
                            .openTime(workingDay ? LocalTime.of(9, 0) : null)
                            .closeTime(workingDay ? LocalTime.of(18, 0) : null)
                            .breakStartTime(workingDay ? LocalTime.of(13, 0) : null)
                            .breakEndTime(workingDay ? LocalTime.of(14, 0) : null)
                            .build();
                })
                .toList();
    }

    private BarberSchedulePeriodSettingsEntity periodSettingsEntity() {
        return BarberSchedulePeriodSettingsEntity.builder()
                .id(PERIOD_SETTINGS_SINGLETON_ID)
                .applyToAllBarbers(true)
                .startDate(today)
                .endDate(today)
                .build();
    }

    private List<BarberSchedulePeriodDaySettingsEntity> periodDaySettingsEntities() {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> BarberSchedulePeriodDaySettingsEntity.builder()
                        .dayOfWeek(day)
                        .workingDay(false)
                        .build())
                .toList();
    }
}

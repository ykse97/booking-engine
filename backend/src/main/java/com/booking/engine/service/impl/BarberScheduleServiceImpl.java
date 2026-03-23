package com.booking.engine.service.impl;

import com.booking.engine.dto.BarberSchedulePeriodDayRequestDto;
import com.booking.engine.dto.BarberSchedulePeriodRequestDto;
import com.booking.engine.dto.BarberSchedulePeriodResponseDto;
import com.booking.engine.dto.BarberScheduleRequestDto;
import com.booking.engine.dto.BarberScheduleResponseDto;
import com.booking.engine.entity.BarberEntity;
import com.booking.engine.entity.BarberDailyScheduleEntity;
import com.booking.engine.entity.BarberSchedulePeriodDaySettingsEntity;
import com.booking.engine.entity.BarberSchedulePeriodSettingsEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.BarberScheduleMapper;
import com.booking.engine.repository.BarberDailyScheduleRepository;
import com.booking.engine.repository.BarberRepository;
import com.booking.engine.repository.BarberSchedulePeriodDaySettingsRepository;
import com.booking.engine.repository.BarberSchedulePeriodSettingsRepository;
import com.booking.engine.service.BarberScheduleService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BarberScheduleService}.
 *
 * Provides per-date schedule retrieval and upsert operations.
 * Dates without records are treated as non-working days.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BarberScheduleServiceImpl implements BarberScheduleService {

    // ---------------------- Constants ----------------------

    private static final int PERIOD_SETTINGS_SINGLETON_ID = 1;

    // ---------------------- Nested Types ----------------------

    private record ScheduleKey(UUID barberId, LocalDate workingDate) {
    }

    // ---------------------- Repositories ----------------------

    private final BarberDailyScheduleRepository scheduleRepository;
    private final BarberRepository barberRepository;
    private final BarberSchedulePeriodSettingsRepository periodSettingsRepository;
    private final BarberSchedulePeriodDaySettingsRepository periodDaySettingsRepository;

    // ---------------------- Mappers ----------------------

    private final BarberScheduleMapper mapper;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BarberScheduleResponseDto> getSchedule(UUID barberId, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusMonths(1);

        Map<LocalDate, BarberDailyScheduleEntity> existing = scheduleRepository
                .findByBarberIdAndWorkingDateBetweenOrderByWorkingDateAsc(barberId, start, end)
                .stream()
                .collect(Collectors.toMap(BarberDailyScheduleEntity::getWorkingDate, e -> e, (a, b) -> a));

        // Return a full continuous range; dates without records are non-working by default.
        return start.datesUntil(end.plusDays(1))
                .map(date -> {
                    BarberDailyScheduleEntity entity = existing.get(date);
                    if (entity != null) {
                        return mapper.toDto(entity);
                    }
                    return BarberScheduleResponseDto.builder()
                            .id(null)
                            .workingDate(date)
                            .workingDay(false)
                            .openTime(null)
                            .closeTime(null)
                            .breakStartTime(null)
                            .breakEndTime(null)
                            .build();
                })
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void upsertDay(UUID barberId, BarberScheduleRequestDto request) {
        log.info("Upserting barber schedule barberId={}, date={}, workingDay={}",
                barberId, request.getWorkingDate(), request.getWorkingDay());

        validateWorkingHours(
                request.getWorkingDay(),
                request.getOpenTime(),
                request.getCloseTime(),
                request.getBreakStartTime(),
                request.getBreakEndTime());

        barberRepository.findById(barberId)
                .orElseThrow(() -> new EntityNotFoundException("Barber", barberId));

        BarberDailyScheduleEntity entity = scheduleRepository
                .findByBarberIdAndWorkingDate(barberId, request.getWorkingDate())
                .orElseGet(() -> {
                    BarberDailyScheduleEntity e = mapper.toEntity(request);
                    e.setBarber(barberRepository.getReferenceById(barberId));
                    return e;
                });

        entity.setWorkingDay(request.getWorkingDay());
        entity.setOpenTime(request.getWorkingDay() ? request.getOpenTime() : null);
        entity.setCloseTime(request.getWorkingDay() ? request.getCloseTime() : null);
        entity.setBreakStartTime(request.getWorkingDay() ? request.getBreakStartTime() : null);
        entity.setBreakEndTime(request.getWorkingDay() ? request.getBreakEndTime() : null);

        scheduleRepository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BarberSchedulePeriodResponseDto getPeriodSettings() {
        BarberSchedulePeriodSettingsEntity settings = getPeriodSettingsEntity();
        List<BarberSchedulePeriodDaySettingsEntity> daySettings = getPeriodDaySettingsEntities();
        return buildPeriodResponse(settings, daySettings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BarberSchedulePeriodResponseDto upsertPeriod(BarberSchedulePeriodRequestDto request) {
        log.info(
                "Upserting barber schedule period start={}, end={}, barberId={}, allBarbers={}",
                request.getStartDate(),
                request.getEndDate(),
                request.getBarberId(),
                request.getApplyToAllBarbers());

        validatePeriodRequest(request);

        Map<DayOfWeek, BarberSchedulePeriodDayRequestDto> scheduleByDay = mapWeeklyConfigs(request.getDays());
        BarberSchedulePeriodSettingsEntity settings = getPeriodSettingsEntity();
        List<BarberSchedulePeriodDaySettingsEntity> daySettings = getPeriodDaySettingsEntities();
        Map<UUID, BarberEntity> targetBarbers = resolveTargetBarbers(request, settings);
        List<UUID> barberIds = new ArrayList<>(targetBarbers.keySet());

        settings.setApplyToAllBarbers(request.getApplyToAllBarbers());
        settings.setStartDate(request.getStartDate());
        settings.setEndDate(request.getEndDate());
        updatePeriodDaySettings(daySettings, scheduleByDay);

        periodSettingsRepository.save(settings);
        periodDaySettingsRepository.saveAll(daySettings);

        Map<ScheduleKey, BarberDailyScheduleEntity> existing = scheduleRepository
                .findByBarberIdInAndWorkingDateBetween(barberIds, request.getStartDate(), request.getEndDate())
                .stream()
                .collect(Collectors.toMap(
                        entity -> new ScheduleKey(entity.getBarber().getId(), entity.getWorkingDate()),
                        entity -> entity,
                        (first, second) -> first,
                        HashMap::new));

        List<BarberDailyScheduleEntity> entitiesToSave = new ArrayList<>();

        request.getStartDate().datesUntil(request.getEndDate().plusDays(1)).forEach(date -> {
            BarberSchedulePeriodDayRequestDto dayConfig = scheduleByDay.get(date.getDayOfWeek());

            targetBarbers.values().forEach(barber -> {
                ScheduleKey key = new ScheduleKey(barber.getId(), date);
                BarberDailyScheduleEntity entity = existing.get(key);

                if (entity == null) {
                    entity = new BarberDailyScheduleEntity();
                    entity.setBarber(barber);
                    entity.setWorkingDate(date);
                }

                applyDayConfig(entity, dayConfig);
                entitiesToSave.add(entity);
            });
        });

        scheduleRepository.saveAll(entitiesToSave);
        return buildPeriodResponse(settings, daySettings);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Copies one weekday configuration into a persisted daily schedule entity and
     * clears all time fields automatically when the day is marked as non-working.
     *
     * @param entity persisted daily schedule target
     * @param config requested weekday configuration
     */
    private void applyDayConfig(BarberDailyScheduleEntity entity, BarberSchedulePeriodDayRequestDto config) {
        entity.setWorkingDay(config.getWorkingDay());
        entity.setOpenTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getOpenTime() : null);
        entity.setCloseTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getCloseTime() : null);
        entity.setBreakStartTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getBreakStartTime() : null);
        entity.setBreakEndTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getBreakEndTime() : null);
    }

    /*
     * Resolves the set of barbers affected by a period update, either by loading
     * all active barbers or by fetching the single requested barber and storing it
     * back into the singleton settings entity.
     *
     * @param request incoming period update request
     * @param settings persisted singleton settings entity
     * @return ordered map of target barber ids to entities
     */
    private Map<UUID, BarberEntity> resolveTargetBarbers(
            BarberSchedulePeriodRequestDto request,
            BarberSchedulePeriodSettingsEntity settings) {
        if (Boolean.TRUE.equals(request.getApplyToAllBarbers())) {
            settings.setTargetBarber(null);
            List<BarberEntity> barbers = barberRepository.findAllByActiveTrueOrderByDisplayOrderAsc();
            if (barbers.isEmpty()) {
                throw new IllegalArgumentException("No active barbers found for period update.");
            }
            return barbers.stream().collect(Collectors.toMap(
                    BarberEntity::getId,
                    barber -> barber,
                    (first, second) -> first,
                    LinkedHashMap::new));
        }

        UUID barberId = request.getBarberId();
        BarberEntity barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new EntityNotFoundException("Barber", barberId));
        settings.setTargetBarber(barber);

        return Map.of(barberId, barber);
    }

    /*
     * Synchronizes persisted weekday settings rows with the validated request map
     * so the admin form reopens with the latest submitted values.
     *
     * @param daySettings persisted weekday setting entities
     * @param scheduleByDay validated request rows indexed by weekday
     */
    private void updatePeriodDaySettings(
            List<BarberSchedulePeriodDaySettingsEntity> daySettings,
            Map<DayOfWeek, BarberSchedulePeriodDayRequestDto> scheduleByDay) {
        daySettings.forEach(entity -> {
            BarberSchedulePeriodDayRequestDto config = scheduleByDay.get(entity.getDayOfWeek());
            entity.setWorkingDay(config.getWorkingDay());
            entity.setOpenTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getOpenTime() : null);
            entity.setCloseTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getCloseTime() : null);
            entity.setBreakStartTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getBreakStartTime() : null);
            entity.setBreakEndTime(Boolean.TRUE.equals(config.getWorkingDay()) ? config.getBreakEndTime() : null);
        });
    }

    /*
     * Builds the API response for the period editor by combining singleton period
     * settings with the seven persisted weekday rows sorted in calendar order.
     *
     * @param settings singleton period settings entity
     * @param daySettings persisted weekday rows
     * @return response DTO for the admin UI
     */
    private BarberSchedulePeriodResponseDto buildPeriodResponse(
            BarberSchedulePeriodSettingsEntity settings,
            List<BarberSchedulePeriodDaySettingsEntity> daySettings) {
        return BarberSchedulePeriodResponseDto.builder()
                .startDate(settings.getStartDate())
                .endDate(settings.getEndDate())
                .barberId(settings.getTargetBarber() != null ? settings.getTargetBarber().getId() : null)
                .applyToAllBarbers(settings.getApplyToAllBarbers())
                .days(daySettings.stream()
                        .sorted(Comparator.comparingInt(item -> item.getDayOfWeek().getValue()))
                        .map(item -> BarberSchedulePeriodDayRequestDto.builder()
                                .dayOfWeek(item.getDayOfWeek())
                                .workingDay(item.getWorkingDay())
                                .openTime(item.getOpenTime())
                                .closeTime(item.getCloseTime())
                                .breakStartTime(item.getBreakStartTime())
                                .breakEndTime(item.getBreakEndTime())
                                .build())
                        .toList())
                .build();
    }

    /*
     * Loads the singleton period settings row and fails fast when the required DB
     * seed data has not been initialized yet.
     *
     * @return singleton period settings entity
     */
    private BarberSchedulePeriodSettingsEntity getPeriodSettingsEntity() {
        return periodSettingsRepository.findById(PERIOD_SETTINGS_SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Barber schedule period settings not initialized. Run DB migrations."));
    }

    /*
     * Loads the persisted weekday setting rows, validates that all seven weekdays
     * exist, and sorts them into calendar order for deterministic processing.
     *
     * @return ordered weekday settings entities
     */
    private List<BarberSchedulePeriodDaySettingsEntity> getPeriodDaySettingsEntities() {
        List<BarberSchedulePeriodDaySettingsEntity> daySettings = new ArrayList<>(periodDaySettingsRepository.findAll());
        if (daySettings.size() != DayOfWeek.values().length) {
            throw new IllegalStateException(
                    "Barber schedule period weekday settings are not initialized. Run DB migrations.");
        }
        daySettings.sort(Comparator.comparingInt(item -> item.getDayOfWeek().getValue()));
        return daySettings;
    }

    /*
     * Converts the incoming weekday list into an enum map, rejects duplicates or
     * missing days, and validates each day's working-hours payload as it is read.
     *
     * @param days request weekday configurations
     * @return validated weekday configuration map
     */
    private Map<DayOfWeek, BarberSchedulePeriodDayRequestDto> mapWeeklyConfigs(
            List<BarberSchedulePeriodDayRequestDto> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("At least one weekday configuration is required.");
        }

        Map<DayOfWeek, BarberSchedulePeriodDayRequestDto> result = new EnumMap<>(DayOfWeek.class);
        for (BarberSchedulePeriodDayRequestDto day : days) {
            if (day.getDayOfWeek() == null) {
                throw new IllegalArgumentException("Day of week is required for every period row.");
            }
            if (result.put(day.getDayOfWeek(), day) != null) {
                throw new IllegalArgumentException("Duplicate weekday configuration: " + day.getDayOfWeek());
            }

            validateWorkingHours(
                    day.getWorkingDay(),
                    day.getOpenTime(),
                    day.getCloseTime(),
                    day.getBreakStartTime(),
                    day.getBreakEndTime());
        }

        if (result.size() != DayOfWeek.values().length) {
            throw new IllegalArgumentException("All seven weekday configurations must be provided.");
        }

        return result;
    }

    /*
     * Validates the high-level period update request before any database work is
     * performed, including date bounds and the mutually exclusive barber targeting
     * rules.
     *
     * @param request incoming period update request
     */
    private void validatePeriodRequest(BarberSchedulePeriodRequestDto request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required for period update.");
        }

        if (request.getApplyToAllBarbers() == null) {
            throw new IllegalArgumentException("Apply-to-all-barbers flag is required for period update.");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date must be earlier than or equal to end date.");
        }

        if (Boolean.TRUE.equals(request.getApplyToAllBarbers()) && request.getBarberId() != null) {
            throw new IllegalArgumentException("Choose either all barbers or one barber for period update.");
        }

        if (!Boolean.TRUE.equals(request.getApplyToAllBarbers()) && request.getBarberId() == null) {
            throw new IllegalArgumentException("Barber id is required when period update is not for all barbers.");
        }
    }

    /*
     * Validates one day's working-hours definition, enforcing open/close ordering
     * and a single 60-minute break fully contained within working hours for days
     * marked as working.
     *
     * @param workingDay whether the day is marked as working
     * @param openTime configured opening time
     * @param closeTime configured closing time
     * @param breakStartTime configured break start
     * @param breakEndTime configured break end
     */
    private void validateWorkingHours(
            Boolean workingDay,
            LocalTime openTime,
            LocalTime closeTime,
            LocalTime breakStartTime,
            LocalTime breakEndTime) {
        if (!Boolean.TRUE.equals(workingDay)) {
            return;
        }

        if (openTime == null || closeTime == null) {
            throw new IllegalArgumentException("Open and close times are required for a working day.");
        }

        if (!openTime.isBefore(closeTime)) {
            throw new IllegalArgumentException("Open time must be earlier than close time.");
        }

        if (breakStartTime == null || breakEndTime == null) {
            throw new IllegalArgumentException("Break start and end times are required for a working day.");
        }

        if (!breakStartTime.isBefore(breakEndTime)) {
            throw new IllegalArgumentException("Break start time must be earlier than break end time.");
        }

        if (Duration.between(breakStartTime, breakEndTime).toMinutes() != 60) {
            throw new IllegalArgumentException("Break time must be exactly 60 minutes.");
        }

        if (breakStartTime.isBefore(openTime) || breakEndTime.isAfter(closeTime)) {
            throw new IllegalArgumentException("Break time must be inside the barber working hours.");
        }
    }
}

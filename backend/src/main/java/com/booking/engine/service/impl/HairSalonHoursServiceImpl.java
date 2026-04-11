package com.booking.engine.service.impl;

import com.booking.engine.dto.HairSalonHoursRequestDto;
import com.booking.engine.dto.HairSalonHoursResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.entity.HairSalonHoursEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.HairSalonHoursMapper;
import com.booking.engine.repository.HairSalonHoursRepository;
import com.booking.engine.repository.HairSalonRepository;
import com.booking.engine.service.HairSalonHoursService;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link HairSalonHoursService}.
 * Provides hair salon hours related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HairSalonHoursServiceImpl implements HairSalonHoursService {
    // ---------------------- Repositories ----------------------

    private final HairSalonRepository hairSalonRepository;

    private final HairSalonHoursRepository hoursRepository;

    // ---------------------- Mappers ----------------------

    private final HairSalonHoursMapper mapper;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public List<HairSalonHoursResponseDto> getWorkingHours(UUID hairSalonId) {
        log.debug("event=hair_salon_hours_get action=start hairSalonId={}", hairSalonId);

        HairSalonEntity salon = hairSalonRepository.findById(hairSalonId)
                .orElseThrow(() -> {
                    log.warn("event=hair_salon_lookup outcome=not_found hairSalonId={}", hairSalonId);
                    return new EntityNotFoundException("HairSalon", hairSalonId);
                });

        List<HairSalonHoursResponseDto> workingHours = salon.getWorkingHours()
                .stream()
                .map(mapper::toDto)
                .toList();
        log.debug("event=hair_salon_hours_get action=success hairSalonId={} resultCount={}",
                hairSalonId,
                workingHours.size());
        return workingHours;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void updateWorkingDay(UUID hairSalonId, DayOfWeek dayOfWeek, HairSalonHoursRequestDto request) {
        log.info("event=hair_salon_hours_update action=start hairSalonId={} dayOfWeek={}", hairSalonId, dayOfWeek);

        HairSalonHoursEntity hours = hoursRepository
                .findByHairSalonIdAndDayOfWeek(hairSalonId, dayOfWeek)
                .orElseThrow(() -> {
                    log.warn(
                            "event=hair_salon_hours_lookup outcome=not_found hairSalonId={} dayOfWeek={}",
                            hairSalonId,
                            dayOfWeek);
                    return new EntityNotFoundException(
                            "Working hours for salon " + hairSalonId + " on " + dayOfWeek);
                });

        if (!request.getWorkingDay()) {
            setNonWorkingDay(hours, hairSalonId, dayOfWeek);
            return;
        }

        setWorkingDay(hours, request, hairSalonId, dayOfWeek);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Sets the day as non-working.
     */
    private void setNonWorkingDay(HairSalonHoursEntity hours, UUID hairSalonId, DayOfWeek dayOfWeek) {
        hours.setWorkingDay(false);
        hours.setOpenTime(null);
        hours.setCloseTime(null);

        log.info("event=hair_salon_hours_update action=set_non_working_day hairSalonId={} dayOfWeek={}",
                hairSalonId,
                dayOfWeek);
    }

    /*
     * Sets the day as working with validated times.
     */
    private void setWorkingDay(HairSalonHoursEntity hours, HairSalonHoursRequestDto request,
            UUID hairSalonId, DayOfWeek dayOfWeek) {
        validateWorkingDayTimes(request);

        hours.setWorkingDay(true);
        hours.setOpenTime(request.getOpenTime());
        hours.setCloseTime(request.getCloseTime());

        log.info("event=hair_salon_hours_update action=success hairSalonId={} dayOfWeek={} openTime={} closeTime={}",
                hairSalonId, dayOfWeek, request.getOpenTime(), request.getCloseTime());
    }

    /*
     * Validates working day times.
     *
     * @param request the request with times
     *
     * @throws IllegalArgumentException if times are invalid
     */
    private void validateWorkingDayTimes(HairSalonHoursRequestDto request) {
        if (request.getOpenTime() == null || request.getCloseTime() == null) {
            throw new IllegalArgumentException("Open and close time must be provided for working day");
        }

        if (!request.getCloseTime().isAfter(request.getOpenTime())) {
            throw new IllegalArgumentException("Close time must be after open time");
        }
    }
}

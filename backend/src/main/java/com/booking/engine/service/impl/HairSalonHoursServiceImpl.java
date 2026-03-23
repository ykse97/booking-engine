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
 * Manages hair salon working hours configuration.
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
        log.info("Fetching working hours for hairSalonId={}", hairSalonId);

        HairSalonEntity salon = hairSalonRepository.findById(hairSalonId)
                .orElseThrow(() -> new EntityNotFoundException("HairSalon", hairSalonId));

        return salon.getWorkingHours()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void updateWorkingDay(UUID hairSalonId, DayOfWeek dayOfWeek, HairSalonHoursRequestDto request) {
        log.info("Updating working hours: hairSalonId={}, day={}", hairSalonId, dayOfWeek);

        HairSalonHoursEntity hours = hoursRepository
                .findByHairSalonIdAndDayOfWeek(hairSalonId, dayOfWeek)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Working hours for salon " + hairSalonId + " on " + dayOfWeek));

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

        log.info("Marked {} as non-working day for hairSalonId={}", dayOfWeek, hairSalonId);
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

        log.info("Working hours successfully updated: hairSalonId={}, day={}, open={}, close={}",
                hairSalonId, dayOfWeek, request.getOpenTime(), request.getCloseTime());
    }

    /*
     * Validates working day times.
     *
     * @param request the request with times
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

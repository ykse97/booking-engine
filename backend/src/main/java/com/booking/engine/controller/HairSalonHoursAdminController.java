package com.booking.engine.controller;

import com.booking.engine.dto.HairSalonHoursRequestDto;
import com.booking.engine.dto.HairSalonHoursResponseDto;
import com.booking.engine.service.HairSalonHoursService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

/**
 * Admin REST controller for hair salon working hours management.
 * Manages weekly schedule configuration.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/admin/hair-salons/{hairSalonId}/hours", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class HairSalonHoursAdminController {

    private final HairSalonHoursService hoursService;

    /**
     * Retrieves working hours for a hair salon.
     *
     * @param hairSalonId the salon ID
     * @return list of working hours for each day
     */
    @GetMapping
    public List<HairSalonHoursResponseDto> getAllHours(@PathVariable UUID hairSalonId) {
        log.info("event=http_request method=GET path=/api/v1/admin/hair-salons/{hairSalonId}/hours hairSalonId={}",
                hairSalonId);
        return hoursService.getWorkingHours(hairSalonId);
    }

    /**
     * Updates working hours for specific day of week.
     *
     * @param hairSalonId the salon ID
     * @param dayOfWeek   the day to update (MONDAY, TUESDAY, etc.)
     * @param request     the hours update request
     * @return 204 No Content on success
     */
    @PutMapping(value = "/{dayOfWeek}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateHours(
            @PathVariable UUID hairSalonId,
            @PathVariable DayOfWeek dayOfWeek,
            @Valid @RequestBody HairSalonHoursRequestDto request) {

        log.info("event=http_request method=PUT path=/api/v1/admin/hair-salons/{hairSalonId}/hours/{dayOfWeek} hairSalonId={} dayOfWeek={}",
                hairSalonId,
                dayOfWeek);
        hoursService.updateWorkingDay(hairSalonId, dayOfWeek, request);
        return ResponseEntity.noContent().build();
    }
}

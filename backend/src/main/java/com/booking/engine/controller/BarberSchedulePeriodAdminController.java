package com.booking.engine.controller;

import com.booking.engine.dto.BarberSchedulePeriodRequestDto;
import com.booking.engine.dto.BarberSchedulePeriodResponseDto;
import com.booking.engine.service.BarberScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller to manage barber schedule updates for date periods.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/admin/barbers/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
public class BarberSchedulePeriodAdminController {

    private final BarberScheduleService scheduleService;

    @GetMapping("/period")
    public BarberSchedulePeriodResponseDto getPeriodSettings() {
        log.info("HTTP GET /api/v1/admin/barbers/schedule/period");
        return scheduleService.getPeriodSettings();
    }

    @PutMapping(value = "/period", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BarberSchedulePeriodResponseDto> upsertPeriod(
            @Valid @RequestBody BarberSchedulePeriodRequestDto request) {
        log.info(
                "HTTP PUT /api/v1/admin/barbers/schedule/period start={} end={} barberId={} allBarbers={}",
                request.getStartDate(),
                request.getEndDate(),
                request.getBarberId(),
                request.getApplyToAllBarbers());
        BarberSchedulePeriodResponseDto updated = scheduleService.upsertPeriod(request);
        return ResponseEntity.ok(updated);
    }
}

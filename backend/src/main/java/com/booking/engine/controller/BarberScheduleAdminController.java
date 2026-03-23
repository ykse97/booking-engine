package com.booking.engine.controller;

import com.booking.engine.dto.BarberScheduleRequestDto;
import com.booking.engine.dto.BarberScheduleResponseDto;
import com.booking.engine.service.BarberScheduleService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller to manage per-date barber schedule.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/admin/barbers/{barberId}/schedule", produces = MediaType.APPLICATION_JSON_VALUE)
public class BarberScheduleAdminController {

    private final BarberScheduleService scheduleService;

    @GetMapping
    public List<BarberScheduleResponseDto> getSchedule(
            @PathVariable UUID barberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.info("HTTP GET /api/v1/admin/barbers/{}/schedule from={} to={}", barberId, from, to);
        return scheduleService.getSchedule(barberId, from, to);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> upsertDay(
            @PathVariable UUID barberId,
            @Valid @RequestBody BarberScheduleRequestDto request) {
        log.info("HTTP PUT /api/v1/admin/barbers/{}/schedule date={}", barberId, request.getWorkingDate());
        scheduleService.upsertDay(barberId, request);
        return ResponseEntity.noContent().build();
    }
}

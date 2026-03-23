package com.booking.engine.controller;

import com.booking.engine.dto.AvailabilitySlotDto;
import com.booking.engine.service.AvailabilityService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public controller exposing read-only availability information.
 */
@RestController
@RequestMapping(value = "/api/v1/public/availability", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    public List<AvailabilitySlotDto> getAvailability(
            @RequestParam UUID barberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam UUID treatmentId) {
        log.info("HTTP GET /api/v1/public/availability barberId={} date={} treatmentId={}", barberId, date, treatmentId);
        return availabilityService.getAvailability(barberId, date, treatmentId);
    }
}

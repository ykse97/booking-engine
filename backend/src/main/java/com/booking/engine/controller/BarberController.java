package com.booking.engine.controller;

import com.booking.engine.dto.BarberResponseDto;
import com.booking.engine.service.BarberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public REST controller for barber queries.
 * Provides read-only access to barber information.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/public/barbers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class BarberController {

    private final BarberService barberService;

    /**
     * Retrieves all active barbers sorted by display order.
     *
     * @return list of barbers in display order
     */
    @GetMapping
    public List<BarberResponseDto> getAllBarbers() {
        log.info("HTTP GET /api/v1/public/barbers");
        return barberService.getAllBarbers();
    }

    /**
     * Retrieves barber by ID.
     *
     * @param id the barber ID
     * @return barber details
     */
    @GetMapping("/{id}")
    public BarberResponseDto getBarberById(@PathVariable UUID id) {
        log.info("HTTP GET /api/v1/public/barbers/{}", id);
        return barberService.getBarberById(id);
    }
}

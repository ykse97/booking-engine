package com.booking.engine.controller;

import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.service.TreatmentService;
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
 * Public REST controller for treatment queries.
 * Provides read-only access to service catalog.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/public/treatments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class TreatmentController {

    private final TreatmentService treatmentService;

    /**
     * Retrieves all active treatments.
     *
     * @return list of treatments
     */
    @GetMapping
    public List<TreatmentResponseDto> getAllTreatments() {
        log.info("HTTP GET /api/v1/public/treatments");
        return treatmentService.getAllTreatments();
    }

    /**
     * Retrieves treatment by ID.
     *
     * @param id the treatment ID
     * @return treatment details
     */
    @GetMapping("/{id}")
    public TreatmentResponseDto getTreatmentById(@PathVariable UUID id) {
        log.info("HTTP GET /api/v1/public/treatments/{}", id);
        return treatmentService.getTreatmentById(id);
    }
}

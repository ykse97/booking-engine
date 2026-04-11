package com.booking.engine.controller;

import com.booking.engine.dto.ReorderRequestDto;
import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.service.TreatmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin REST controller for treatment management.
 * Provides CRUD operations for services/treatments (admin only).
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/admin/treatments", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class AdminTreatmentController {

    private final TreatmentService treatmentService;

    /**
     * Creates a new treatment.
     *
     * @param request the treatment creation request
     * @return created treatment with generated ID
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TreatmentResponseDto> createTreatment(
            @Valid @RequestBody TreatmentRequestDto request) {

        log.info("event=http_request method=POST path=/api/v1/admin/treatments requestedOrder={}",
                request.getDisplayOrder());
        TreatmentResponseDto created = treatmentService.createTreatment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates existing treatment.
     *
     * @param id      the treatment ID
     * @param request the update request
     * @return 204 No Content on success
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateTreatment(
            @PathVariable UUID id,
            @Valid @RequestBody TreatmentRequestDto request) {

        log.info("event=http_request method=PUT path=/api/v1/admin/treatments/{id} treatmentId={}", id);
        treatmentService.updateTreatment(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Removes treatment by ID.
     *
     * @param id the treatment ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeTreatment(@PathVariable UUID id) {
        log.info("event=http_request method=DELETE path=/api/v1/admin/treatments/{id} treatmentId={}", id);
        treatmentService.removeTreatment(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorders two treatments by swapping their positions.
     *
     * @param request contains two treatment IDs to swap
     * @return 204 No Content on success
     */
    @PostMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorderTreatments(
            @Valid @RequestBody ReorderRequestDto request) {
        log.info("event=http_request method=POST path=/api/v1/admin/treatments/reorder treatmentId1={} treatmentId2={}",
                request.getId1(), request.getId2());
        treatmentService.reorderTreatments(
                request.getId1(), request.getId2());
        return ResponseEntity.noContent().build();
    }
}

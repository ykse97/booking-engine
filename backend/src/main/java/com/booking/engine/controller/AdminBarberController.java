package com.booking.engine.controller;

import com.booking.engine.dto.BarberRequestDto;
import com.booking.engine.dto.BarberResponseDto;
import com.booking.engine.dto.ReorderRequestDto;
import com.booking.engine.service.BarberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin REST controller for barber management.
 * Provides CRUD operations for barbers (admin only).
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/admin/barbers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class AdminBarberController {

    private final BarberService barberService;

    /**
     * Creates a new barber.
     *
     * @param request the barber creation request
     * @return created barber with generated ID
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BarberResponseDto> createBarber(
            @Valid @RequestBody BarberRequestDto request) {

        log.info("HTTP POST /api/v1/admin/barbers name={}, requestedOrder={}",
                request.getName(), request.getDisplayOrder());
        BarberResponseDto created = barberService.createBarber(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates existing barber.
     *
     * @param id      the barber ID
     * @param request the update request
     * @return 204 No Content on success
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateBarber(
            @PathVariable UUID id,
            @Valid @RequestBody BarberRequestDto request) {

        log.info("HTTP PUT /api/v1/admin/barbers/{}", id);
        barberService.updateBarber(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes barber by ID.
     *
     * @param id the barber ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBarber(@PathVariable UUID id) {
        log.info("HTTP DELETE /api/v1/admin/barbers/{}", id);
        barberService.deleteBarber(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorders two barbers by swapping their positions.
     *
     * @param request contains two barber IDs to swap
     * @return 204 No Content on success
     */
    @PostMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorderBarbers(
            @Valid @RequestBody ReorderRequestDto request) {
        log.info("HTTP POST /api/v1/admin/barbers/reorder {} <-> {}",
                request.getId1(), request.getId2());
        barberService.reorderBarbers(request.getId1(), request.getId2());
        return ResponseEntity.noContent().build();
    }
}

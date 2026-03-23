package com.booking.engine.service;

import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import java.util.List;
import java.util.UUID;

/**
 * Service contract for treatment management operations.
 * Provides CRUD-style methods for treatment catalog and ordering.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
public interface TreatmentService {

    /**
     * Creates a new treatment.
     *
     * @param request treatment request payload
     * @return created treatment DTO
     */
    TreatmentResponseDto createTreatment(TreatmentRequestDto request);

    /**
     * Retrieves all active treatments sorted by display order.
     *
     * @return list of treatment DTOs
     */
    List<TreatmentResponseDto> getAllTreatments();

    /**
     * Updates treatment data.
     *
     * @param id      treatment identifier
     * @param request update payload
     */
    void updateTreatment(UUID id, TreatmentRequestDto request);

    /**
     * Removes treatment by identifier.
     *
     * @param id treatment identifier
     */
    void removeTreatment(UUID id);

    /**
     * Retrieves treatment by identifier.
     *
     * @param id treatment identifier
     * @return treatment DTO
     */
    TreatmentResponseDto getTreatmentById(UUID id);

    /**
     * Swaps order of two treatments.
     *
     * @param treatmentId1 first treatment
     * @param treatmentId2 second treatment
     */
    void reorderTreatments(UUID treatmentId1, UUID treatmentId2);
}
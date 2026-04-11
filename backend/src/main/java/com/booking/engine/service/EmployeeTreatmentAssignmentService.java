package com.booking.engine.service;

import com.booking.engine.entity.TreatmentEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service contract for employee treatment assignment operations.
 * Defines employee treatment assignment related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface EmployeeTreatmentAssignmentService {

    /**
     * Resolves requested treatments.
     *
     * @param treatmentIds treatment ids value
     * @return result value
     */
    Set<TreatmentEntity> resolveRequestedTreatments(List<UUID> treatmentIds);
}

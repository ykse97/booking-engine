package com.booking.engine.service.impl;

import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.TreatmentMapper;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.DisplayOrderService;
import com.booking.engine.service.TreatmentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TreatmentService}.
 * Provides treatment related business operations.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreatmentServiceImpl implements TreatmentService {
    // ---------------------- Repositories ----------------------

    private final TreatmentRepository treatmentRepository;

    // ---------------------- Mappers ----------------------

    private final TreatmentMapper mapper;

    // ---------------------- Services ----------------------

    private final DisplayOrderService displayOrderService;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public TreatmentResponseDto createTreatment(TreatmentRequestDto request) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        log.info("event=treatment_create action=start requestedOrder={}",
                request.getDisplayOrder());

        Integer order = displayOrderService.resolveDisplayOrder(
                request.getDisplayOrder(), treatmentRepository);

        displayOrderService.shiftDisplayOrders(order, treatmentRepository);

        TreatmentEntity treatment = mapper.toEntity(request);
        treatment.setDisplayOrder(order);

        TreatmentEntity savedTreatment = treatmentRepository.save(treatment);

        log.info("event=treatment_create action=success treatmentId={} displayOrder={}",
                savedTreatment.getId(), savedTreatment.getDisplayOrder());

        return mapper.toDto(savedTreatment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TreatmentResponseDto> getAllTreatments() {
        log.debug("event=treatment_list action=start");

        List<TreatmentResponseDto> result = treatmentRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(mapper::toDto)
                .toList();

        log.debug("event=treatment_list action=success resultCount={}", result.size());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TreatmentResponseDto getTreatmentById(UUID id) {
        log.debug("event=treatment_get action=start treatmentId={}", id);

        TreatmentEntity treatment = findTreatmentOrThrow(id);

        log.debug("event=treatment_get action=success treatmentId={}", id);
        return mapper.toDto(treatment);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void updateTreatment(UUID id, TreatmentRequestDto request) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        log.info("event=treatment_update action=start treatmentId={}", id);

        TreatmentEntity treatment = findTreatmentOrThrow(id);

        Integer oldOrder = treatment.getDisplayOrder();
        Integer newOrder = displayOrderService.resolveDisplayOrderForUpdate(
                request.getDisplayOrder(), oldOrder, treatmentRepository);

        if (!oldOrder.equals(newOrder)) {
            displayOrderService.moveDisplayOrder(oldOrder, newOrder, treatmentRepository);
        }

        mapper.updateFromDto(request, treatment);
        treatment.setDisplayOrder(newOrder);

        log.info("event=treatment_update action=success treatmentId={} oldDisplayOrder={} newDisplayOrder={}",
                id, oldOrder, newOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void removeTreatment(UUID id) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        log.info("event=treatment_delete action=start treatmentId={}", id);

        TreatmentEntity treatment = findTreatmentOrThrow(id);
        Integer removedOrder = treatment.getDisplayOrder();

        treatmentRepository.delete(treatment);

        displayOrderService.shiftOrdersAfterDelete(removedOrder, treatmentRepository);

        log.info("event=treatment_delete action=success treatmentId={} freedDisplayOrder={}",
                id, removedOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void reorderTreatments(UUID treatmentId1, UUID treatmentId2) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        log.info("event=treatment_reorder action=start treatmentId1={} treatmentId2={}", treatmentId1, treatmentId2);

        if (treatmentId1.equals(treatmentId2)) {
            throw new IllegalArgumentException("Cannot reorder the same treatment id");
        }

        TreatmentEntity treatment1 = findTreatmentOrThrow(treatmentId1);
        TreatmentEntity treatment2 = findTreatmentOrThrow(treatmentId2);

        Integer order1 = treatment1.getDisplayOrder();
        Integer order2 = treatment2.getDisplayOrder();

        Integer tempOrder = displayOrderService.resolveTemporaryDisplayOrder(treatmentRepository);

        treatment1.setDisplayOrder(tempOrder);
        treatmentRepository.saveAndFlush(treatment1);

        treatment2.setDisplayOrder(order1);
        treatmentRepository.saveAndFlush(treatment2);

        treatment1.setDisplayOrder(order2);
        treatmentRepository.saveAndFlush(treatment1);

        log.info(
                "event=treatment_reorder action=success treatmentId1={} newDisplayOrder1={} treatmentId2={} newDisplayOrder2={}",
                treatmentId1, order2, treatmentId2, order1);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Finds treatment by ID or throws exception.
     *
     * @param id the treatment UUID
     *
     * @return the treatment entity
     *
     * @throws EntityNotFoundException if not found
     */
    private TreatmentEntity findTreatmentOrThrow(UUID id) {
        return treatmentRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> {
                    log.warn("event=treatment_lookup outcome=not_found treatmentId={}", id);
                    return new EntityNotFoundException("Treatment", id);
                });
    }
}

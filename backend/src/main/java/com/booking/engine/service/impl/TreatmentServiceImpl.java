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
 * Manages treatment catalog and website ordering.
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

        log.info("Creating treatment name={}, requestedOrder={}",
                request.getName(), request.getDisplayOrder());

        Integer order = displayOrderService.resolveDisplayOrder(
                request.getDisplayOrder(), treatmentRepository);

        displayOrderService.shiftDisplayOrders(order, treatmentRepository);

        TreatmentEntity treatment = mapper.toEntity(request);
        treatment.setDisplayOrder(order);

        TreatmentEntity savedTreatment = treatmentRepository.save(treatment);

        log.info("Treatment created successfully with id={}, order={}",
                savedTreatment.getId(), savedTreatment.getDisplayOrder());

        return mapper.toDto(savedTreatment);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TreatmentResponseDto> getAllTreatments() {
        log.info("Retrieving all active treatments");

        List<TreatmentResponseDto> result = treatmentRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(mapper::toDto)
                .toList();

        log.info("Retrieved {} active treatments", result.size());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TreatmentResponseDto getTreatmentById(UUID id) {
        log.info("Retrieving treatment with id={}", id);

        TreatmentEntity treatment = findTreatmentOrThrow(id);

        log.info("Treatment retrieved successfully with id={}", id);
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

        log.info("Updating treatment with id={}", id);

        TreatmentEntity treatment = findTreatmentOrThrow(id);

        Integer oldOrder = treatment.getDisplayOrder();
        Integer newOrder = displayOrderService.resolveDisplayOrderForUpdate(
                request.getDisplayOrder(), oldOrder, treatmentRepository);

        if (!oldOrder.equals(newOrder)) {
            displayOrderService.moveDisplayOrder(oldOrder, newOrder, treatmentRepository);
        }

        mapper.updateFromDto(request, treatment);
        treatment.setDisplayOrder(newOrder);

        log.info("Treatment updated successfully with id={}, oldOrder={}, newOrder={}",
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

        log.info("Removing treatment with id={}", id);

        TreatmentEntity treatment = findTreatmentOrThrow(id);
        Integer removedOrder = treatment.getDisplayOrder();

        treatmentRepository.delete(treatment);

        displayOrderService.shiftOrdersAfterDelete(removedOrder, treatmentRepository);

        log.info("Treatment removed successfully with id={}, freedOrder={}",
                id, removedOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void reorderTreatments(UUID treatmentId1, UUID treatmentId2) {

        // Lock active treatment ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(treatmentRepository);

        log.info("Swapping treatments {} <-> {}", treatmentId1, treatmentId2);

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

        log.info("Swap complete {}->{}, {}->{}",
                treatmentId1, order2, treatmentId2, order1);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Finds treatment by ID or throws exception.
     *
     * @param id the treatment UUID
     * @return the treatment entity
     * @throws EntityNotFoundException if not found
     */
    private TreatmentEntity findTreatmentOrThrow(UUID id) {
        return treatmentRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> {
                    log.warn("Treatment not found with ID={}", id);
                    return new EntityNotFoundException("Treatment", id);
                });
    }
}

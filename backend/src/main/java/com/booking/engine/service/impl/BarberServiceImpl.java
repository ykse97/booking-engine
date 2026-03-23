package com.booking.engine.service.impl;

import com.booking.engine.dto.BarberRequestDto;
import com.booking.engine.dto.BarberResponseDto;
import com.booking.engine.entity.BarberEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.BarberMapper;
import com.booking.engine.repository.BarberRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.service.BarberService;
import com.booking.engine.service.DisplayOrderService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BarberService}.
 * Manages barber catalog and website ordering.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BarberServiceImpl implements BarberService {

    // ---------------------- Repositories ----------------------

    private final BarberRepository barberRepository;
    private final BookingRepository bookingRepository;

    // ---------------------- Mappers ----------------------

    private final BarberMapper mapper;

    // ---------------------- Services ----------------------

    private final DisplayOrderService displayOrderService;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public BarberResponseDto createBarber(BarberRequestDto request) {

        // Lock active barber ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(barberRepository);

        log.info("Creating barber name={}, requestedOrder={}",
                request.getName(), request.getDisplayOrder());

        Integer order = displayOrderService.resolveDisplayOrder(
                request.getDisplayOrder(), barberRepository);

        displayOrderService.shiftDisplayOrders(order, barberRepository);

        BarberEntity barber = mapper.toEntity(request);
        barber.setDisplayOrder(order);

        BarberEntity saved = barberRepository.save(barber);

        log.info("Barber created id={}, order={}",
                saved.getId(), saved.getDisplayOrder());

        return mapper.toDto(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BarberResponseDto> getAllBarbers() {

        log.info("Retrieving all barbers");

        return barberRepository.findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BarberResponseDto getBarberById(UUID id) {

        log.info("Retrieving barber with id={}", id);

        BarberEntity barber = findBarberOrThrow(id);

        return mapper.toDto(barber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateBarber(UUID id, BarberRequestDto request) {

        // Lock active barber ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(barberRepository);

        log.info("Updating barber id={}", id);

        BarberEntity barber = findBarberOrThrow(id);

        Integer oldOrder = barber.getDisplayOrder();
        Integer newOrder = displayOrderService.resolveDisplayOrderForUpdate(
                request.getDisplayOrder(), oldOrder, barberRepository);

        if (!oldOrder.equals(newOrder)) {
            displayOrderService.moveDisplayOrder(oldOrder, newOrder, barberRepository);
        }

        mapper.updateFromDto(request, barber);
        barber.setDisplayOrder(newOrder);

        log.info("Barber updated id={}, oldOrder={}, newOrder={}",
                id, oldOrder, newOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteBarber(UUID id) {

        // Lock active barber ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(barberRepository);

        log.info("Deleting barber id={}", id);

        BarberEntity barber = findBarberOrThrow(id);

        validateNoActiveBookings(id);

        Integer deletedOrder = barber.getDisplayOrder();

        barberRepository.delete(barber);

        displayOrderService.shiftOrdersAfterDelete(deletedOrder, barberRepository);

        log.info("Barber hard-deleted id={}, freedOrder={}", id, deletedOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void reorderBarbers(UUID barberId1, UUID barberId2) {

        // Lock active barber ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(barberRepository);

        log.info("Swapping barbers {} <-> {}", barberId1, barberId2);

        if (barberId1.equals(barberId2)) {
            throw new IllegalArgumentException("Cannot reorder the same barber id");
        }

        BarberEntity barber1 = findBarberOrThrow(barberId1);
        BarberEntity barber2 = findBarberOrThrow(barberId2);

        Integer order1 = barber1.getDisplayOrder();
        Integer order2 = barber2.getDisplayOrder();

        Integer tempOrder = displayOrderService.resolveTemporaryDisplayOrder(barberRepository);

        barber1.setDisplayOrder(tempOrder);
        barberRepository.saveAndFlush(barber1);

        barber2.setDisplayOrder(order1);
        barberRepository.saveAndFlush(barber2);

        barber1.setDisplayOrder(order2);
        barberRepository.saveAndFlush(barber1);

        log.info("Swap complete {}->{}, {}->{}",
                barberId1, order2, barberId2, order1);
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Retrieves active barber or throws exception.
     *
     * @param id barber id
     * @return barber entity
     */
    private BarberEntity findBarberOrThrow(UUID id) {

        return barberRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EntityNotFoundException("Barber", id));
    }

    /*
     * Validates that barber has no active bookings.
     * Final booking statuses (CANCELLED, EXPIRED) are allowed for deletion.
     *
     * @param barberId barber identifier
     */
    private void validateNoActiveBookings(UUID barberId) {
        boolean hasActiveBookings = bookingRepository.existsByBarberIdAndStatusIn(
                barberId,
                List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));

        if (hasActiveBookings) {
            log.warn("Cannot delete barber id={} because active bookings exist", barberId);
            throw new IllegalStateException(
                    "Cannot delete barber while active bookings exist (PENDING or CONFIRMED).");
        }
    }
}

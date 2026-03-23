package com.booking.engine.service;

import com.booking.engine.entity.DisplayOrderedEntity;
import com.booking.engine.repository.DisplayOrderRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Shared service for display order resolution and shifting logic.
 * Encapsulates common business rules for ordered active entities.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@Service
public class DisplayOrderService {

    // ---------------------- Public Methods ----------------------

    /**
     * Resolves effective display order for a create operation.
     * If requested order is null, appends entity to the end of current active list.
     *
     * @param requestedOrder requested display order, may be null
     * @param repository     repository providing ordering operations
     * @param <T>            ordered entity type
     * @return resolved display order
     * @throws IllegalArgumentException if requested order is out of range
     */
    public <T extends DisplayOrderedEntity> Integer resolveDisplayOrder(
            Integer requestedOrder,
            DisplayOrderRepository<T> repository) {

        if (requestedOrder == null) {
            return repository.findMaxDisplayOrderByActiveTrue()
                    .map(max -> max + 1)
                    .orElse(0);
        }

        validateDisplayOrderForCreate(requestedOrder, repository);
        return requestedOrder;
    }

    /**
     * Resolves effective display order for an update operation.
     * If requested order is null, keeps current order unchanged.
     *
     * @param requestedOrder requested new display order, may be null
     * @param currentOrder   current entity display order
     * @param repository     repository providing ordering operations
     * @param <T>            ordered entity type
     * @return resolved display order
     * @throws IllegalArgumentException if requested order is out of range
     */
    public <T extends DisplayOrderedEntity> Integer resolveDisplayOrderForUpdate(
            Integer requestedOrder,
            Integer currentOrder,
            DisplayOrderRepository<T> repository) {

        Integer resolvedOrder = requestedOrder == null
                ? currentOrder
                : requestedOrder;

        validateDisplayOrderForUpdate(resolvedOrder, repository);
        return resolvedOrder;
    }

    /**
     * Validates requested display order for create operation.
     *
     * @param order      requested display order
     * @param repository repository providing ordering operations
     * @param <T>        ordered entity type
     * @throws IllegalArgumentException if requested order is greater than max + 1
     */
    public <T extends DisplayOrderedEntity> void validateDisplayOrderForCreate(
            Integer order,
            DisplayOrderRepository<T> repository) {

        Integer max = repository.findMaxDisplayOrderByActiveTrue().orElse(-1);

        if (order > max + 1) {
            throw new IllegalArgumentException(
                    "Display order cannot be greater than " + (max + 1));
        }
    }

    /**
     * Validates requested display order for update operation.
     *
     * @param order      requested display order
     * @param repository repository providing ordering operations
     * @param <T>        ordered entity type
     * @throws IllegalArgumentException if requested order is greater than current
     *                                  max
     */
    public <T extends DisplayOrderedEntity> void validateDisplayOrderForUpdate(
            Integer order,
            DisplayOrderRepository<T> repository) {

        Integer max = repository.findMaxDisplayOrderByActiveTrue().orElse(-1);

        if (order > max) {
            throw new IllegalArgumentException(
                    "Display order cannot be greater than " + max);
        }
    }

    /**
     * Shifts active entities starting from given order by one position down.
     * Used before inserting a new entity into requested order slot.
     *
     * @param fromOrder  starting display order
     * @param repository repository providing ordering operations
     * @param <T>        ordered entity type
     */
    public <T extends DisplayOrderedEntity> void shiftDisplayOrders(
            Integer fromOrder,
            DisplayOrderRepository<T> repository) {

        List<T> toShift = repository.findByActiveTrueAndDisplayOrderGreaterThanEqual(fromOrder);

        toShift.stream()
                .sorted(Comparator.comparing(DisplayOrderedEntity::getDisplayOrder).reversed())
                .forEach(entity -> entity.setDisplayOrder(entity.getDisplayOrder() + 1));
    }

    /**
     * Moves entity display order while keeping sequence integrity.
     *
     * @param oldOrder   current order
     * @param newOrder   target order
     * @param repository repository providing ordering operations
     * @param <T>        ordered entity type
     */
    public <T extends DisplayOrderedEntity> void moveDisplayOrder(
            Integer oldOrder,
            Integer newOrder,
            DisplayOrderRepository<T> repository) {

        if (oldOrder.equals(newOrder)) {
            return;
        }

        if (oldOrder < newOrder) {
            List<T> entities = repository.findByActiveTrueAndDisplayOrderBetween(oldOrder + 1, newOrder);
            entities.forEach(entity -> entity.setDisplayOrder(entity.getDisplayOrder() - 1));
        } else {
            List<T> entities = repository.findByActiveTrueAndDisplayOrderBetween(newOrder, oldOrder - 1);
            entities.forEach(entity -> entity.setDisplayOrder(entity.getDisplayOrder() + 1));
        }
    }

    /**
     * Shifts display orders after entity deletion.
     *
     * @param removedOrder removed entity display order
     * @param repository   repository providing ordering operations
     * @param <T>          ordered entity type
     */
    public <T extends DisplayOrderedEntity> void shiftOrdersAfterDelete(
            Integer removedOrder,
            DisplayOrderRepository<T> repository) {

        List<T> toShift = repository.findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(removedOrder);
        toShift.forEach(entity -> entity.setDisplayOrder(entity.getDisplayOrder() - 1));
    }

    /**
     * Returns temporary display order value used for conflict-free swaps.
     *
     * @param repository repository providing ordering operations
     * @param <T>        ordered entity type
     * @return temporary display order outside active range
     */
    public <T extends DisplayOrderedEntity> Integer resolveTemporaryDisplayOrder(
            DisplayOrderRepository<T> repository) {

        Optional<Integer> max = repository.findMaxDisplayOrderByActiveTrue();
        return max.map(value -> value + 1).orElse(0);
    }

    /**
     * Locks full active ordering scope for current entity type.
     * Used to prevent concurrent reorder conflicts.
     *
     * @param repository repository providing locking query
     * @param <T>        ordered entity type
     */
    public <T extends DisplayOrderedEntity> void lockActiveOrderingScope(
            DisplayOrderRepository<T> repository) {

        repository.findAllActiveForUpdateOrderByDisplayOrderAsc();
    }
}

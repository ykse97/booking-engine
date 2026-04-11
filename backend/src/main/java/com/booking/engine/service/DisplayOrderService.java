package com.booking.engine.service;

import com.booking.engine.entity.DisplayOrderedEntity;
import com.booking.engine.repository.DisplayOrderRepository;

/**
 * Service contract for display order operations.
 * Defines display order related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface DisplayOrderService {

    /**
     * Resolves display order.
     *
     * @param requestedOrder requested display order
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> Integer resolveDisplayOrder(
            Integer requestedOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Resolves display order for update.
     *
     * @param requestedOrder requested display order
     * @param currentOrder current display order
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> Integer resolveDisplayOrderForUpdate(
            Integer requestedOrder,
            Integer currentOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Validates display order for create.
     *
     * @param order display order
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> void validateDisplayOrderForCreate(
            Integer order,
            DisplayOrderRepository<T> repository);

    /**
     * Validates display order for update.
     *
     * @param order display order
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> void validateDisplayOrderForUpdate(
            Integer order,
            DisplayOrderRepository<T> repository);

    /**
     * Shifts display orders.
     *
     * @param fromOrder from order value
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> void shiftDisplayOrders(
            Integer fromOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Moves display order.
     *
     * @param oldOrder previous display order
     * @param newOrder new display order
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> void moveDisplayOrder(
            Integer oldOrder,
            Integer newOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Shifts orders after delete.
     *
     * @param removedOrder removed display order
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> void shiftOrdersAfterDelete(
            Integer removedOrder,
            DisplayOrderRepository<T> repository);

    /**
     * Resolves temporary display order.
     *
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> Integer resolveTemporaryDisplayOrder(
            DisplayOrderRepository<T> repository);

    /**
     * Locks active ordering scope.
     *
     * @param repository repository instance
     * @return result value
     */
    <T extends DisplayOrderedEntity> void lockActiveOrderingScope(
            DisplayOrderRepository<T> repository);
}

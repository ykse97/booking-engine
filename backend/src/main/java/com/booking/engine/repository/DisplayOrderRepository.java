package com.booking.engine.repository;

import com.booking.engine.entity.DisplayOrderedEntity;
import java.util.List;
import java.util.Optional;

/**
 * Shared repository contract for entities that support active display ordering.
 *
 * @param <T> ordered entity type
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
public interface DisplayOrderRepository<T extends DisplayOrderedEntity> {

    /**
     * Finds maximum display order among active entities.
     *
     * @return optional maximum display order
     */
    Optional<Integer> findMaxDisplayOrderByActiveTrue();

    /**
     * Finds active entities with display order greater or equal to given value.
     *
     * @param order starting order
     * @return matching entities
     */
    List<T> findByActiveTrueAndDisplayOrderGreaterThanEqual(Integer order);

    /**
     * Finds active entities with display order greater than given value
     * ordered ascending by display order.
     *
     * @param order starting order
     * @return matching entities
     */
    List<T> findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(Integer order);

    /**
     * Finds active entities with display order within inclusive range.
     *
     * @param start start order
     * @param end   end order
     * @return matching entities
     */
    List<T> findByActiveTrueAndDisplayOrderBetween(Integer start, Integer end);

    /**
     * Locks all active ordered entities for write in ascending display order.
     * Used to serialize admin ordering operations and avoid conflicts.
     *
     * @return locked active entities
     */
    List<T> findAllActiveForUpdateOrderByDisplayOrderAsc();
}
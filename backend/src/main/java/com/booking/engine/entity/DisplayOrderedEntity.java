package com.booking.engine.entity;

/**
 * Contract for entities that participate in active display ordering.
 * Used by shared ordering services to manage insert and reorder operations.
 *
 * @author Yehor
 * @version 1.0
 * @since March 13, 2026
 */
public interface DisplayOrderedEntity {

    /**
     * Returns current display order.
     *
     * @return display order
     */
    Integer getDisplayOrder();

    /**
     * Updates display order.
     *
     * @param displayOrder new display order
     */
    void setDisplayOrder(Integer displayOrder);

    /**
     * Returns whether entity is active.
     *
     * @return true if active
     */
    Boolean getActive();
}
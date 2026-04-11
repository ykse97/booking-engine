package com.booking.engine.service;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;

/**
 * Service contract for booking audit operations.
 * Defines booking audit related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since April 2026
 */
public interface BookingAuditService {

    /**
     * Executes audit admin booking created.
     *
     * @param booking booking entity
     */
    void auditAdminBookingCreated(BookingEntity booking);

    /**
     * Executes audit admin booking updated.
     *
     * @param booking booking entity
     * @param previousStatus previous status value
     */
    void auditAdminBookingUpdated(BookingEntity booking, BookingStatus previousStatus);

    /**
     * Executes audit admin booking cancelled.
     *
     * @param booking booking entity
     * @param previousStatus previous status value
     */
    void auditAdminBookingCancelled(BookingEntity booking, BookingStatus previousStatus);

    /**
     * Executes mask customer email for logs.
     *
     * @param email email value
     * @return result value
     */
    String maskCustomerEmailForLogs(String email);

    /**
     * Checks whether h search for logs.
     *
     * @param search search text
     * @return result value
     */
    String hashSearchForLogs(String search);
}

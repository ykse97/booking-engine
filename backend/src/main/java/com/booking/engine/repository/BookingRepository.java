package com.booking.engine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link BookingEntity} persistence and queries.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    /**
     * Finds bookings for barber, date and a set of statuses.
     *
     * @param barberId barber identifier
     * @param bookingDate booking date
     * @param statuses statuses to include
     * @return matching bookings
     */
    List<BookingEntity> findByBarberIdAndBookingDateAndStatusIn(
            UUID barberId,
            LocalDate bookingDate,
            List<BookingStatus> statuses);

    /**
     * Finds booking by Stripe PaymentIntent id.
     *
     * @param paymentIntentId Stripe PaymentIntent id
     * @return optional booking
     */
    Optional<BookingEntity> findByStripePaymentIntentId(String paymentIntentId);

    /**
     * Finds booking by Stripe PaymentIntent id with pessimistic write lock.
     *
     * @param paymentIntentId Stripe PaymentIntent id
     * @return locked booking
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingEntity b WHERE b.stripePaymentIntentId = :paymentIntentId")
    Optional<BookingEntity> findByStripePaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);

    /**
     * Finds booking by id with pessimistic write lock.
     *
     * @param id booking identifier
     * @return locked booking
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingEntity b WHERE b.id = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Checks whether a barber has at least one booking
     * with status in provided list.
     *
     * @param barberId barber identifier
     * @param statuses booking statuses to match
     * @return true if matching bookings exist
     */
    boolean existsByBarberIdAndStatusIn(UUID barberId, List<BookingStatus> statuses);

    /**
     * Finds bookings with the given status that have already expired by timestamp.
     *
     * @param status booking status to match
     * @param now cutoff timestamp for expiration
     * @return matching expired bookings
     */
    List<BookingEntity> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime now);

    /**
     * Marks confirmed bookings as done when their end date/time is already in the past.
     *
     * @param today current date
     * @param nowTime current local time
     * @param currentStatus status to replace
     * @param newStatus target status
     * @return number of updated rows
     */
    @Modifying
    @Query("""
                UPDATE BookingEntity b
                SET b.status = :newStatus
                WHERE b.status = :currentStatus
                AND (
                    b.bookingDate < :today
                    OR (b.bookingDate = :today AND b.endTime < :nowTime)
                )
            """)
    int completeConfirmedBookings(
            LocalDate today,
            LocalTime nowTime,
            BookingStatus currentStatus,
            BookingStatus newStatus);

    /**
     * Finds all active bookings with barber and treatment eagerly loaded.
     *
     * @return active bookings with related entities
     */
    @Query("""
                SELECT b
                FROM BookingEntity b
                JOIN FETCH b.barber
                JOIN FETCH b.treatment
                WHERE b.active = TRUE
            """)
    List<BookingEntity> findAllActiveWithBarberAndTreatment();

    /**
     * Counts active bookings by status.
     *
     * @param status booking status
     * @return number of active bookings with requested status
     */
    long countByActiveTrueAndStatus(BookingStatus status);

    /**
     * Counts active unpaid slot holds for a client IP address.
     *
     * @param clientIp client IP address
     * @param status hold status to match
     * @param now current timestamp
     * @return number of active unpaid holds
     */
    @Query("""
                SELECT COUNT(b)
                FROM BookingEntity b
                WHERE b.active = TRUE
                AND b.status = :status
                AND b.paymentCapturedAt IS NULL
                AND b.expiresAt IS NOT NULL
                AND b.expiresAt > :now
                AND b.holdClientIp = :clientIp
            """)
    long countActiveUnpaidHoldsByClientIp(
            @Param("clientIp") String clientIp,
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now);

    /**
     * Counts active unpaid slot holds for a client device identifier.
     *
     * @param clientDeviceId persistent client device identifier
     * @param status hold status to match
     * @param now current timestamp
     * @return number of active unpaid holds
     */
    @Query("""
                SELECT COUNT(b)
                FROM BookingEntity b
                WHERE b.active = TRUE
                AND b.status = :status
                AND b.paymentCapturedAt IS NULL
                AND b.expiresAt IS NOT NULL
                AND b.expiresAt > :now
                AND b.holdClientDeviceId = :clientDeviceId
            """)
    long countActiveUnpaidHoldsByClientDeviceId(
            @Param("clientDeviceId") String clientDeviceId,
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now);

}

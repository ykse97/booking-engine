package com.booking.engine.repository;

import com.booking.engine.entity.BarberEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing {@link BarberEntity}.
 * Provides database operations for barber persistence
 * and ordering logic.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@Repository
public interface BarberRepository extends JpaRepository<BarberEntity, UUID>,
        DisplayOrderRepository<BarberEntity> {

    /**
     * Retrieves all active barbers sorted by display order.
     *
     * @return ordered list of active barbers
     */
    List<BarberEntity> findAllByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Finds maximum display order value among active barbers.
     *
     * @return optional maximum order
     */
    @Override
    @Query("SELECT MAX(b.displayOrder) FROM BarberEntity b WHERE b.active = true")
    Optional<Integer> findMaxDisplayOrderByActiveTrue();

    /**
     * Finds active barbers with display order greater or equal to given value.
     *
     * @param order starting order
     * @return list of barbers
     */
    @Override
    List<BarberEntity> findByActiveTrueAndDisplayOrderGreaterThanEqual(Integer order);

    /**
     * Finds active barbers with display order greater than given value.
     *
     * @param order starting order
     * @return list of barbers
     */
    @Override
    List<BarberEntity> findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(Integer order);

    /**
     * Finds active barbers with display order within range.
     *
     * @param start start order
     * @param end   end order
     * @return list of barbers
     */
    @Override
    List<BarberEntity> findByActiveTrueAndDisplayOrderBetween(Integer start, Integer end);

    /**
     * Finds active barber by id.
     *
     * @param id barber id
     * @return optional barber
     */
    Optional<BarberEntity> findByIdAndActiveTrue(UUID id);

    /**
     * Locks all active barbers for write in display order.
     * Used to serialize admin ordering operations and avoid conflicts.
     *
     * @return locked list of active barbers
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BarberEntity b WHERE b.active = true ORDER BY b.displayOrder ASC")
    List<BarberEntity> findAllActiveForUpdateOrderByDisplayOrderAsc();

    /**
     * Finds active barber by id with pessimistic write lock.
     * Used to serialize booking creation per barber and prevent double-booking race
     * conditions.
     *
     * @param id barber id
     * @return optional active barber entity
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BarberEntity b WHERE b.id = :id AND b.active = true")
    Optional<BarberEntity> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

}

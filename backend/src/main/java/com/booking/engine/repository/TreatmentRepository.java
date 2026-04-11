package com.booking.engine.repository;

import com.booking.engine.entity.TreatmentEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link TreatmentEntity}.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Repository
public interface TreatmentRepository extends JpaRepository<TreatmentEntity, UUID>,
        DisplayOrderRepository<TreatmentEntity> {
    /**
     * Retrieves all active treatments sorted by display order.
     *
     * @return ordered list of treatments
     */
    List<TreatmentEntity> findAllByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Finds maximum display order value.
     *
     * @return optional maximum order
     */
    @Override
    @Query("SELECT MAX(t.displayOrder) FROM TreatmentEntity t WHERE t.active = true")
    Optional<Integer> findMaxDisplayOrderByActiveTrue();

    /**
     * Finds treatments with display order greater or equal to given value.
     *
     * @param order starting order
     * @return list of treatments
     */
    @Override
    List<TreatmentEntity> findByActiveTrueAndDisplayOrderGreaterThanEqual(Integer order);

    /**
     * Finds treatments with display order greater than given value.
     *
     * @param order starting order
     * @return list of treatments
     */
    @Override
    List<TreatmentEntity> findByActiveTrueAndDisplayOrderGreaterThanOrderByDisplayOrderAsc(Integer order);

    /**
     * Finds treatments with display order within range.
     *
     * @param start start order
     * @param end   end order
     * @return list of treatments
     */
    @Override
    List<TreatmentEntity> findByActiveTrueAndDisplayOrderBetween(Integer start, Integer end);

    /**
     * Finds treatment by id only if active.
     *
     * @param id treatment id
     * @return optional treatment entity
     */
    Optional<TreatmentEntity> findByIdAndActiveTrue(UUID id);

    /**
     * Finds all active treatments by ids.
     *
     * @param ids treatment ids
     * @return matching active treatments
     */
    List<TreatmentEntity> findAllByIdInAndActiveTrue(Set<UUID> ids);

    /**
     * Finds active treatment by id with pessimistic write lock.
     * Used to serialize booking creation per treatment and prevent double-booking
     * race conditions.
     *
     * @param id treatment id
     * @return optional active treatment entity
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TreatmentEntity t WHERE t.id = :id AND t.active = true")
    Optional<TreatmentEntity> findByIdAndActiveTrueForUpdate(@Param("id") UUID id);

    /**
     * Locks all active treatments for write in display order.
     * Used to serialize admin ordering operations and avoid conflicts.
     *
     * @return locked list of active treatments
     */
    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TreatmentEntity t WHERE t.active = true ORDER BY t.displayOrder ASC")
    List<TreatmentEntity> findAllActiveForUpdateOrderByDisplayOrderAsc();

}

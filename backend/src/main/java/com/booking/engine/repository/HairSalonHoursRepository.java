package com.booking.engine.repository;

import java.util.Optional;
import java.util.UUID;
import java.time.DayOfWeek;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.booking.engine.entity.HairSalonHoursEntity;

/**
 * Repository for managing {@link HairSalonHoursEntity} persistence.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Repository
public interface HairSalonHoursRepository
        extends JpaRepository<HairSalonHoursEntity, UUID> {

    /**
     * Finds salon working hours record for a specific weekday.
     *
     * @param hairSalonId salon identifier
     * @param dayOfWeek weekday
     * @return optional working-hours entity
     */
    Optional<HairSalonHoursEntity> findByHairSalonIdAndDayOfWeek(
            UUID hairSalonId,
            DayOfWeek dayOfWeek);
}

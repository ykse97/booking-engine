package com.booking.engine.repository;

import com.booking.engine.entity.BarberSchedulePeriodSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for the singleton per-period barber schedule form state.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Repository
public interface BarberSchedulePeriodSettingsRepository extends JpaRepository<BarberSchedulePeriodSettingsEntity, Integer> {
}

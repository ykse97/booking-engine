package com.booking.engine.repository;

import com.booking.engine.entity.BarberSchedulePeriodDaySettingsEntity;
import java.time.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisted weekly rows of the admin per-period barber schedule form.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Repository
public interface BarberSchedulePeriodDaySettingsRepository
        extends JpaRepository<BarberSchedulePeriodDaySettingsEntity, DayOfWeek> {
}

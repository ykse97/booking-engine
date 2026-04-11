package com.booking.engine.repository;

import com.booking.engine.entity.EmployeeSchedulePeriodDaySettingsEntity;
import java.time.DayOfWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for persisted weekly rows of the admin per-period employee schedule form.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Repository
public interface EmployeeSchedulePeriodDaySettingsRepository
        extends JpaRepository<EmployeeSchedulePeriodDaySettingsEntity, DayOfWeek> {
}

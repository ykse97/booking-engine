package com.booking.engine.repository;

import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for per-date employee schedules.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Repository
public interface EmployeeDailyScheduleRepository extends JpaRepository<EmployeeDailyScheduleEntity, UUID> {

    /**
     * Finds employee schedule for a specific working date.
     *
     * @param employeeId employee identifier
     * @param workingDate target working date
     * @return optional schedule entity
     */
    Optional<EmployeeDailyScheduleEntity> findByEmployeeIdAndWorkingDate(UUID employeeId, LocalDate workingDate);

    /**
     * Finds employee schedules in an inclusive date range ordered ascending by date.
     *
     * @param employeeId employee identifier
     * @param start range start date
     * @param end range end date
     * @return matching schedule entities
     */
    List<EmployeeDailyScheduleEntity> findByEmployeeIdAndWorkingDateBetweenOrderByWorkingDateAsc(
            UUID employeeId, LocalDate start, LocalDate end);

    /**
     * Finds schedules for multiple employees in an inclusive date range.
     *
     * @param employeeIds employee identifiers
     * @param start range start date
     * @param end range end date
     * @return matching schedule entities
     */
    List<EmployeeDailyScheduleEntity> findByEmployeeIdInAndWorkingDateBetween(
            Collection<UUID> employeeIds, LocalDate start, LocalDate end);

    /**
     * Deletes employee schedules older than the provided cutoff date.
     *
     * @param workingDate exclusive lower retention bound
     * @return number of deleted schedule rows
     */
    long deleteByWorkingDateBefore(LocalDate workingDate);
}

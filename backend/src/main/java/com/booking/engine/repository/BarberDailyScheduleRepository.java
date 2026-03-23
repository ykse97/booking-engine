package com.booking.engine.repository;

import com.booking.engine.entity.BarberDailyScheduleEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for per-date barber schedules.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Repository
public interface BarberDailyScheduleRepository extends JpaRepository<BarberDailyScheduleEntity, UUID> {

    /**
     * Finds barber schedule for a specific working date.
     *
     * @param barberId barber identifier
     * @param workingDate target working date
     * @return optional schedule entity
     */
    Optional<BarberDailyScheduleEntity> findByBarberIdAndWorkingDate(UUID barberId, LocalDate workingDate);

    /**
     * Finds barber schedules in an inclusive date range ordered ascending by date.
     *
     * @param barberId barber identifier
     * @param start range start date
     * @param end range end date
     * @return matching schedule entities
     */
    List<BarberDailyScheduleEntity> findByBarberIdAndWorkingDateBetweenOrderByWorkingDateAsc(
            UUID barberId, LocalDate start, LocalDate end);

    /**
     * Finds schedules for multiple barbers in an inclusive date range.
     *
     * @param barberIds barber identifiers
     * @param start range start date
     * @param end range end date
     * @return matching schedule entities
     */
    List<BarberDailyScheduleEntity> findByBarberIdInAndWorkingDateBetween(
            Collection<UUID> barberIds, LocalDate start, LocalDate end);
}

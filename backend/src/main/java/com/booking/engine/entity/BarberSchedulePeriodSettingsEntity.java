package com.booking.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Singleton entity that stores the latest admin per-period barber schedule form state.
 */
@Entity
@Table(name = "barber_schedule_period_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberSchedulePeriodSettingsEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_barber_id")
    private BarberEntity targetBarber;

    @Column(name = "apply_to_all_barbers", nullable = false)
    private Boolean applyToAllBarbers;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
}

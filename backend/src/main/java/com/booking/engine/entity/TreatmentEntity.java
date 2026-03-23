package com.booking.engine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * Entity representing a treatment/service.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Entity
@Table(name = "treatment")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TreatmentEntity extends BaseEntity implements DisplayOrderedEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * Display order for sorting treatments on website.
     * Lower values = first in list.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}

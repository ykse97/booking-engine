package com.booking.engine.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing a barber/employee.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Entity
@Table(name = "barber")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BarberEntity extends BaseEntity implements DisplayOrderedEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /**
     * Display order for sorting barbers on website.
     * Lower values = first in list.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}

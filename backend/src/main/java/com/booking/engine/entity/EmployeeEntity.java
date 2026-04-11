package com.booking.engine.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Entity representing a employee/employee.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Entity
@Table(name = "employee")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeEntity extends BaseEntity implements DisplayOrderedEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    /**
     * Display order for sorting employees on website.
     * Lower values = first in list.
     */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Builder.Default
    @Column(name = "bookable", nullable = false)
    private Boolean bookable = false;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "employee_treatment",
            joinColumns = @JoinColumn(name = "employee_id"),
            inverseJoinColumns = @JoinColumn(name = "treatment_id"))
    private Set<TreatmentEntity> providedTreatments = new LinkedHashSet<>();
}

package com.booking.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating/updating a barber.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberRequestDto {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
    private String name;

    @NotBlank(message = "Role is required")
    @Size(max = 255, message = "Role cannot exceed 255 characters")
    private String role;

    @Size(max = 2000, message = "Bio cannot exceed 2000 characters")
    private String bio;

    @Size(max = 500, message = "Photo URL cannot exceed 500 characters")
    private String photoUrl;

    /**
     * Display order for sorting (0 = first).
     * If not provided, barber will be added to end.
     */
    @PositiveOrZero(message = "Display order must be zero or positive")
    private Integer displayOrder;
}

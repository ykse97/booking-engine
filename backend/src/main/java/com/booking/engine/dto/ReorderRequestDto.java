package com.booking.engine.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for reordering entities as barbers, treatments.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderRequestDto {

    @NotNull(message = "First entity ID is required")
    private UUID id1;

    @NotNull(message = "Second entity ID is required")
    private UUID id2;
}
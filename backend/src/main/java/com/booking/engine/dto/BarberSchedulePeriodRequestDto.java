package com.booking.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bulk request DTO for applying barber schedule rules to a date period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberSchedulePeriodRequestDto {

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private UUID barberId;

    @NotNull
    private Boolean applyToAllBarbers;

    @Valid
    @NotEmpty
    private List<BarberSchedulePeriodDayRequestDto> days;
}

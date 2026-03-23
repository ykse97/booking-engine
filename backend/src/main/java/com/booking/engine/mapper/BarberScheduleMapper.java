package com.booking.engine.mapper;

import com.booking.engine.dto.BarberScheduleRequestDto;
import com.booking.engine.dto.BarberScheduleResponseDto;
import com.booking.engine.entity.BarberDailyScheduleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for barber daily schedule entity and DTOs.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Mapper(componentModel = "spring")
public interface BarberScheduleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "barber", ignore = true)
    BarberDailyScheduleEntity toEntity(BarberScheduleRequestDto dto);

    BarberScheduleResponseDto toDto(BarberDailyScheduleEntity entity);
}

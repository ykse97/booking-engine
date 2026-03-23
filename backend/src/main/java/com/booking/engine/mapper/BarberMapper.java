package com.booking.engine.mapper;

import com.booking.engine.dto.BarberRequestDto;
import com.booking.engine.dto.BarberResponseDto;
import com.booking.engine.entity.BarberEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for Barber entity and DTOs.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Mapper(config = GlobalMapperConfig.class)
public interface BarberMapper extends BaseMapper<BarberEntity, BarberRequestDto, BarberResponseDto> {

    /**
     * {@inheritDoc}
     * Ignores all audit fields and sets active to true for new entities.
     */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BarberEntity toEntity(BarberRequestDto request);
}
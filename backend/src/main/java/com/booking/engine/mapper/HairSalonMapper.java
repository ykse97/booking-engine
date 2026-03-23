package com.booking.engine.mapper;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for HairSalon entity and DTOs.
 * Uses HairSalonHoursMapper for nested working hours mapping.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Mapper(config = GlobalMapperConfig.class, uses = HairSalonHoursMapper.class)
public interface HairSalonMapper extends BaseMapper<HairSalonEntity, HairSalonRequestDto, HairSalonResponseDto> {

    /**
     * {@inheritDoc}
     * Ignores audit fields and working hours relationship.
     */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "workingHours", ignore = true)
    HairSalonEntity toEntity(HairSalonRequestDto request);

    /**
     * {@inheritDoc}
     * Maps working hours using injected HairSalonHoursMapper.
     */
    @Override
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "address", source = "address")
    @Mapping(target = "workingHours", source = "workingHours")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    HairSalonResponseDto toDto(HairSalonEntity entity);
}
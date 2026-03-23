package com.booking.engine.mapper;

import com.booking.engine.dto.HairSalonHoursResponseDto;
import com.booking.engine.entity.HairSalonHoursEntity;
import org.mapstruct.Mapper;

/**
 * Mapper for HairSalonHours entity and DTOs.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Mapper(config = GlobalMapperConfig.class)
public interface HairSalonHoursMapper {

    /**
     * Converts entity to response DTO.
     *
     * @param entity the working hours entity
     * @return response DTO
     */
    HairSalonHoursResponseDto toDto(HairSalonHoursEntity entity);
}

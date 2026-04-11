package com.booking.engine.mapper;

import com.booking.engine.dto.EmployeeScheduleRequestDto;
import com.booking.engine.dto.EmployeeScheduleResponseDto;
import com.booking.engine.entity.EmployeeDailyScheduleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for employee daily schedule entity and DTOs.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Mapper(componentModel = "spring")
public interface EmployeeScheduleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employee", ignore = true)
    EmployeeDailyScheduleEntity toEntity(EmployeeScheduleRequestDto dto);

    EmployeeScheduleResponseDto toDto(EmployeeDailyScheduleEntity entity);
}

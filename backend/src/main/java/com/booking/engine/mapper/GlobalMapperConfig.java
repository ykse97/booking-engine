package com.booking.engine.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Global MapStruct configuration.
 * Applied to all mappers in the application.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@MapperConfig(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GlobalMapperConfig {
}
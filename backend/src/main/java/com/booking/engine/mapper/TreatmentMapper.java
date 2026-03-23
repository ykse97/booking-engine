package com.booking.engine.mapper;

import com.booking.engine.dto.TreatmentRequestDto;
import com.booking.engine.dto.TreatmentResponseDto;
import com.booking.engine.entity.TreatmentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for Treatment entity and DTOs.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Mapper(config = GlobalMapperConfig.class)
public interface TreatmentMapper extends BaseMapper<TreatmentEntity, TreatmentRequestDto, TreatmentResponseDto> {

    /**
     * {@inheritDoc}
     * Ignores audit fields and sets active to true for new entities.
     */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TreatmentEntity toEntity(TreatmentRequestDto request);
}
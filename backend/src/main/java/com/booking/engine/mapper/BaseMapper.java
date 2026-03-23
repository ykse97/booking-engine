package com.booking.engine.mapper;

import org.mapstruct.InheritConfiguration;
import org.mapstruct.MappingTarget;

/**
 * Base mapper interface for entity-DTO conversions.
 * Defines standard CRUD mapping operations.
 *
 * @param <E>   Entity type
 * @param <Req> Request DTO type
 * @param <Res> Response DTO type
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public interface BaseMapper<E, Req, Res> {

    /**
     * Converts request DTO to entity.
     * Ignores audit fields (id, createdAt, updatedAt) and active status.
     *
     * @param request the request DTO
     * @return new entity instance
     */
    E toEntity(Req request);

    /**
     * Converts entity to response DTO.
     *
     * @param entity the entity
     * @return response DTO
     */
    Res toDto(E entity);

    /**
     * Updates existing entity from request DTO.
     * Ignores null values and audit fields.
     *
     * @param request the request DTO with updates
     * @param entity  the target entity to update
     */
    @InheritConfiguration(name = "toEntity")
    void updateFromDto(Req request, @MappingTarget E entity);
}
package com.booking.engine.mapper;

import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.entity.BookingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for Booking entity and DTOs.
 * Handles complex mappings including nested customer DTO.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Mapper(config = GlobalMapperConfig.class)
public interface BookingMapper extends BaseMapper<BookingEntity, BookingRequestDto, BookingResponseDto> {

    /**
     * {@inheritDoc}
     * Maps nested customer DTO to flat entity fields.
     * Ignores relationships and audit fields.
     */
    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "barber", ignore = true)
    @Mapping(target = "treatment", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    @Mapping(target = "stripePaymentIntentId", ignore = true)
    @Mapping(target = "stripePaymentStatus", ignore = true)
    @Mapping(target = "holdAmount", ignore = true)
    @Mapping(target = "holdClientIp", ignore = true)
    @Mapping(target = "holdClientDeviceId", ignore = true)
    @Mapping(target = "paymentCapturedAt", ignore = true)
    @Mapping(target = "paymentReleasedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // Map nested customer fields to flat entity fields
    @Mapping(target = "customerName", source = "customer.name")
    @Mapping(target = "customerEmail", source = "customer.email")
    @Mapping(target = "customerPhone", source = "customer.phone")
    BookingEntity toEntity(BookingRequestDto request);

    /**
     * {@inheritDoc}
     * Maps flat entity fields to nested customer DTO.
     */
    @Override
    @Mapping(target = "barberId", source = "barber.id")
    @Mapping(target = "barberName", source = "barber.name")
    @Mapping(target = "treatmentId", source = "treatment.id")
    @Mapping(target = "treatmentName", source = "treatment.name")
    // Map flat entity fields to nested customer DTO
    @Mapping(target = "customerName", source = "customerName")
    @Mapping(target = "customerEmail", source = "customerEmail")
    @Mapping(target = "customerPhone", source = "customerPhone")
    BookingResponseDto toDto(BookingEntity entity);
}

package com.booking.engine.service.impl;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.entity.HairSalonEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.HairSalonMapper;
import com.booking.engine.properties.HairSalonProperties;
import com.booking.engine.repository.HairSalonRepository;
import com.booking.engine.service.HairSalonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link HairSalonService}.
 * Provides hair salon related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since February 27, 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HairSalonServiceImpl implements HairSalonService {
    // ---------------------- Repositories ----------------------

    private final HairSalonRepository hairSalonRepository;

    // ---------------------- Mappers ----------------------

    private final HairSalonMapper mapper;

    // ---------------------- Properties ----------------------

    /** Default hair salon ID configuration. */
    private final HairSalonProperties properties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public HairSalonResponseDto getHairSalonData() {
        log.debug("event=hair_salon_get action=start hairSalonId={}", properties.getId());

        HairSalonEntity salon = findSalonOrThrow(properties.getId());

        log.debug("event=hair_salon_get action=success hairSalonId={}", properties.getId());
        return mapper.toDto(salon);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void updateHairSalonData(HairSalonRequestDto request) {
        log.info("event=hair_salon_update action=start hairSalonId={}", properties.getId());

        HairSalonEntity salon = findSalonOrThrow(properties.getId());
        mapper.updateFromDto(request, salon);

        log.info("event=hair_salon_update action=success hairSalonId={}", properties.getId());
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Finds hair salon by ID or throws exception.
     *
     * @param id the salon ID
     *
     * @return the hair salon entity
     *
     * @throws EntityNotFoundException if not found
     */
    private HairSalonEntity findSalonOrThrow(java.util.UUID id) {
        return hairSalonRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("event=hair_salon_lookup outcome=not_found hairSalonId={}", id);
                    return new EntityNotFoundException("HairSalon", id);
                });
    }
}

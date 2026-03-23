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
 * Manages hair salon data configuration and settings.
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

    // ---------------------- Properties ----------------------

    /** Default hair salon ID configuration. */
    private final HairSalonProperties properties;

    // ---------------------- Repositories ----------------------

    private final HairSalonRepository hairSalonRepository;

    // ---------------------- Mappers ----------------------

    private final HairSalonMapper mapper;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public HairSalonResponseDto getHairSalonData() {
        log.info("Fetching hair salon data with ID={}", properties.getId());

        HairSalonEntity salon = findSalonOrThrow(properties.getId());

        log.info("Hair salon data fetched successfully with ID={}", properties.getId());
        return mapper.toDto(salon);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void updateHairSalonData(HairSalonRequestDto request) {
        log.info("Updating hair salon with ID={}", properties.getId());

        HairSalonEntity salon = findSalonOrThrow(properties.getId());
        mapper.updateFromDto(request, salon);

        log.info("Hair salon data updated successfully with ID={}", properties.getId());
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Finds hair salon by ID or throws exception.
     *
     * @param id the salon ID
     * @return the hair salon entity
     * @throws EntityNotFoundException if not found
     */
    private HairSalonEntity findSalonOrThrow(java.util.UUID id) {
        return hairSalonRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Hair salon not found with ID={}", id);
                    return new EntityNotFoundException("HairSalon", id);
                });
    }
}

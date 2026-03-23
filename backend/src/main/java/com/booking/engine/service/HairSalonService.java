package com.booking.engine.service;

import com.booking.engine.dto.HairSalonRequestDto;
import com.booking.engine.dto.HairSalonResponseDto;

/**
 * Service contract for singleton hair salon configuration.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
public interface HairSalonService {

    /**
     * Retrieves singleton hair salon data.
     *
     * @return hair salon DTO
     */
    HairSalonResponseDto getHairSalonData();

    /**
     * Updates singleton hair salon data.
     *
     * @param request update payload
     */
    void updateHairSalonData(HairSalonRequestDto request);
}

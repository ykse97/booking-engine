package com.booking.engine.service;

import com.booking.engine.dto.BarberRequestDto;
import com.booking.engine.dto.BarberResponseDto;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for barber management.
 *
 * Handles creation, update, deletion and ordering logic.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
public interface BarberService {

    /**
     * Creates new barber.
     *
     * @param request barber data
     * @return created barber
     */
    BarberResponseDto createBarber(BarberRequestDto request);

    /**
     * Retrieves all barbers sorted by display order.
     *
     * @return list of barbers
     */
    List<BarberResponseDto> getAllBarbers();

    /**
     * Retrieves barber by id.
     *
     * @param id barber id
     * @return barber data
     */
    BarberResponseDto getBarberById(UUID id);

    /**
     * Updates barber data.
     *
     * @param id      barber id
     * @param request new data
     */
    void updateBarber(UUID id, BarberRequestDto request);

    /**
     * Deletes barber from database.
     *
     * @param id barber id
     */
    void deleteBarber(UUID id);

    /**
     * Swaps order of two barbers.
     *
     * @param barberId1 first barber
     * @param barberId2 second barber
     */
    void reorderBarbers(UUID barberId1, UUID barberId2);
}
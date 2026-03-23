package com.booking.engine.controller;

import com.booking.engine.dto.HairSalonResponseDto;
import com.booking.engine.service.HairSalonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST controller for hair salon information.
 * Provides read-only access to salon details.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/public/hair-salon", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class HairSalonController {

    private final HairSalonService hairSalonService;

    /**
     * Retrieves hair salon data.
     * Returns singleton salon configuration.
     *
     * @return salon details
     */
    @GetMapping
    public HairSalonResponseDto getHairSalonData() {
        log.info("HTTP GET /api/v1/public/hair-salon");
        return hairSalonService.getHairSalonData();
    }
}

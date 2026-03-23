package com.booking.engine.service;

import com.booking.engine.dto.LoginRequestDto;
import com.booking.engine.dto.LoginResponseDto;

/**
 * Service contract for JWT authentication.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
public interface AuthService {

    /**
     * Authenticates admin user and issues JWT access token.
     *
     * @param request login credentials
     * @return access token response
     */
    LoginResponseDto login(LoginRequestDto request);
}


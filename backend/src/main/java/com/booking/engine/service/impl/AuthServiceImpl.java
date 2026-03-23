package com.booking.engine.service.impl;

import com.booking.engine.dto.LoginRequestDto;
import com.booking.engine.dto.LoginResponseDto;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.JwtService;
import com.booking.engine.service.AuthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link AuthService}.
 * Performs username/password authentication and issues JWT token.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    // ---------------------- Security Components ----------------------

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    // ---------------------- Properties ----------------------

    private final JwtProperties jwtProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        log.info("Authenticating admin user username={}", request.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails, Map.of());

        return LoginResponseDto.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInSeconds(jwtProperties.getExpirationSeconds())
                .build();
    }
}

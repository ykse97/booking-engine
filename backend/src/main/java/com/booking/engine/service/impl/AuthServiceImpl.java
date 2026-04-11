package com.booking.engine.service.impl;

import com.booking.engine.dto.LoginRequestDto;
import com.booking.engine.dto.LoginResponseDto;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.AdminAuthenticationSecurityService;
import com.booking.engine.security.AdminUserDetailsService;
import com.booking.engine.security.JwtService;
import com.booking.engine.security.SecurityAuditLogger;
import com.booking.engine.service.AuthService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link AuthService}.
 * Provides auth related business operations.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    // ---------------------- Services ----------------------

    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;

    private final AdminAuthenticationSecurityService adminAuthenticationSecurityService;

    private final AdminUserDetailsService adminUserDetailsService;

    private final SecurityAuditLogger securityAuditLogger;

    // ---------------------- Properties ----------------------

    private final JwtProperties jwtProperties;
    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        log.debug("event=auth_login action=start");

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            adminAuthenticationSecurityService.registerSuccessfulLogin(userDetails.getUsername());
            int tokenVersion = adminUserDetailsService.loadTokenVersionByUsername(userDetails.getUsername());
            String token = jwtService.generateToken(userDetails, Map.of(), tokenVersion);
            String principalFingerprint = principalFingerprint(userDetails.getUsername());
            securityAuditLogger.log(securityAuditLogger.event("AUTH_LOGIN", "SUCCESS")
                    .actorUsername(userDetails.getUsername())
                    .resourceType("ADMIN_ACCOUNT")
                    .resourceId(userDetails.getUsername())
                    .build());
            log.info("event=auth_login action=success principalFingerprint={}", principalFingerprint);

            return LoginResponseDto.builder()
                    .username(userDetails.getUsername())
                    .accessToken(token)
                    .expiresInSeconds(jwtProperties.getExpirationSeconds())
                    .build();
        } catch (AuthenticationException ex) {
            if (!(ex instanceof LockedException)) {
                adminAuthenticationSecurityService.registerFailedLogin(request.getUsername());
            }
            Map<String, Object> additionalFields = new LinkedHashMap<>();
            String principalFingerprint = principalFingerprint(request.getUsername());
            if (principalFingerprint != null) {
                additionalFields.put("principalFingerprint", principalFingerprint);
            }
            securityAuditLogger.log(securityAuditLogger.event("AUTH_LOGIN", "FAILURE")
                    .resourceType("ADMIN_ACCOUNT")
                    .reasonCode(ex instanceof LockedException ? "ACCOUNT_LOCKED" : "INVALID_CREDENTIALS")
                    .additionalFields(additionalFields)
                    .build());
            log.warn(
                    "event=auth_login outcome=failure principalFingerprint={} reasonCode={}",
                    principalFingerprint,
                    ex instanceof LockedException ? "ACCOUNT_LOCKED" : "INVALID_CREDENTIALS");
            throw ex;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logout(String username) {
        adminAuthenticationSecurityService.logout(username);
        log.info("event=auth_logout action=success principalFingerprint={}", principalFingerprint(username));
    }

    // ---------------------- Private Methods ----------------------

    /**
     * Hashes the login principal for operational logs.
     */
    private String principalFingerprint(String username) {
        return securityAuditLogger.hashValue(username);
    }
}

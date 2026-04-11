package com.booking.engine.controller;

import com.booking.engine.dto.AdminSessionResponseDto;
import com.booking.engine.properties.JwtProperties;
import com.booking.engine.security.AdminAuthCookieService;
import com.booking.engine.service.AuthService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated admin auth endpoints.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/admin/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminAuthController {

    private final AuthService authService;
    private final AdminAuthCookieService adminAuthCookieService;
    private final JwtProperties jwtProperties;

    @GetMapping("/session")
    public ResponseEntity<AdminSessionResponseDto> session(Principal principal) {
        log.info("event=http_request method=GET path=/api/v1/admin/auth/session");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AdminSessionResponseDto.builder()
                        .username(principal.getName())
                        .expiresInSeconds(jwtProperties.getExpirationSeconds())
                        .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal) {
        log.info("event=http_request method=POST path=/api/v1/admin/auth/logout");
        authService.logout(principal.getName());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, adminAuthCookieService.clearSessionCookie().toString())
                .build();
    }
}

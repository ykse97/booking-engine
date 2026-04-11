package com.booking.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Service DTO for successful admin login.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    private String username;

    private String accessToken;

    private long expiresInSeconds;
}

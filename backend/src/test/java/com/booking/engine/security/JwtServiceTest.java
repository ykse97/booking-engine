package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.properties.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

    @Test
    void generateTokenAndValidateWorksWithRawSecretFallback() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("raw-secret-for-tests-12345678901234567890!");
        properties.setExpirationSeconds(3600);
        properties.setIssuer("booking-engine");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("admin")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails, Map.of("scope", "admin"));

        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void generateTokenAndValidateWorksWithBase64Secret() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(Base64.getEncoder().encodeToString(
                "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)));
        properties.setExpirationSeconds(3600);
        properties.setIssuer("booking-engine");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("owner")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails, Map.of());

        assertThat(jwtService.extractUsername(token)).isEqualTo("owner");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void tokenExtractionFailsWhenExpired() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("another-raw-secret-for-tests-123456789012345!");
        properties.setExpirationSeconds(-5);
        properties.setIssuer("booking-engine");
        JwtService jwtService = new JwtService(properties);

        UserDetails userDetails = User.withUsername("expired-user")
                .password("ignored")
                .authorities("ROLE_ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails, Map.of());

        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails))
                .isInstanceOf(ExpiredJwtException.class);
    }
}

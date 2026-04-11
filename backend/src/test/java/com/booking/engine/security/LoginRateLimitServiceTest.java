package com.booking.engine.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.AuthSecurityProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LoginRateLimitServiceTest {

    @Test
    void registerAttemptRejectsRequestsThatExceedIpLimit() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getRateLimit().getIp().setMaxAttempts(2);
        properties.getRateLimit().getIp().setWindowSeconds(60);
        properties.getRateLimit().getUsernameIp().setMaxAttempts(5);
        properties.getRateLimit().getUsernameIp().setWindowSeconds(60);
        SecurityAuditLogger securityAuditLogger = Mockito.mock(SecurityAuditLogger.class);
        Mockito.when(securityAuditLogger.hashValue(Mockito.anyString())).thenReturn("fingerprint");
        LoginRateLimitService service = new LoginRateLimitService(properties, securityAuditLogger);

        service.registerAttempt("127.0.0.1", "admin");
        service.registerAttempt("127.0.0.1", "admin");

        assertThatThrownBy(() -> service.registerAttempt("127.0.0.1", "admin"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many login attempts. Please try again later.");
    }

    @Test
    void resetSuccessfulAttemptClearsOnlyUsernameIpBucket() {
        AuthSecurityProperties properties = new AuthSecurityProperties();
        properties.getRateLimit().getIp().setMaxAttempts(2);
        properties.getRateLimit().getIp().setWindowSeconds(60);
        properties.getRateLimit().getUsernameIp().setMaxAttempts(1);
        properties.getRateLimit().getUsernameIp().setWindowSeconds(60);
        SecurityAuditLogger securityAuditLogger = Mockito.mock(SecurityAuditLogger.class);
        Mockito.when(securityAuditLogger.hashValue(Mockito.anyString())).thenReturn("fingerprint");
        LoginRateLimitService service = new LoginRateLimitService(properties, securityAuditLogger);

        service.registerAttempt("127.0.0.1", "admin");
        service.resetSuccessfulAttempt("127.0.0.1", "admin");

        assertThatCode(() -> service.registerAttempt("127.0.0.1", "admin"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.registerAttempt("127.0.0.1", "other-admin"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many login attempts. Please try again later.");
    }
}

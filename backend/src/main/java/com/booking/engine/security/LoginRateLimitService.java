package com.booking.engine.security;

import com.booking.engine.exception.RateLimitExceededException;
import com.booking.engine.properties.AuthSecurityProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * In-memory rate limiter for admin login attempts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRateLimitService {

    private static final String KEY_PREFIX_IP = "ip:";
    private static final String KEY_PREFIX_USERNAME_IP = "user-ip:";

    private final AuthSecurityProperties authSecurityProperties;
    private final SecurityAuditLogger securityAuditLogger;

    private final ConcurrentMap<String, AttemptWindow> ipAttempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttemptWindow> usernameIpAttempts = new ConcurrentHashMap<>();

    public void registerAttempt(String clientIp, String username) {
        Instant now = Instant.now();
        String normalizedIp = normalize(clientIp);
        String normalizedUsername = normalize(username);

        if (!consume(ipAttempts,
                KEY_PREFIX_IP + normalizedIp,
                authSecurityProperties.getRateLimit().getIp().getMaxAttempts(),
                Duration.ofSeconds(authSecurityProperties.getRateLimit().getIp().getWindowSeconds()),
                now)) {
            log.warn("Login rate limit exceeded ipFingerprint={}", fingerprint(normalizedIp));
            throw new RateLimitExceededException(AuthSecurityMessages.TOO_MANY_LOGIN_ATTEMPTS);
        }

        if (!normalizedUsername.isBlank()
                && !consume(usernameIpAttempts,
                KEY_PREFIX_USERNAME_IP + normalizedIp + ":" + normalizedUsername,
                authSecurityProperties.getRateLimit().getUsernameIp().getMaxAttempts(),
                Duration.ofSeconds(authSecurityProperties.getRateLimit().getUsernameIp().getWindowSeconds()),
                now)) {
            log.warn(
                    "Login rate limit exceeded ipFingerprint={} principalFingerprint={}",
                    fingerprint(normalizedIp),
                    fingerprint(normalizedUsername));
            throw new RateLimitExceededException(AuthSecurityMessages.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    public void resetSuccessfulAttempt(String clientIp, String username) {
        String normalizedIp = normalize(clientIp);
        String normalizedUsername = normalize(username);
        if (normalizedUsername.isBlank()) {
            return;
        }

        // Keep the broader shared-IP throttle intact so one successful login
        // does not clear recent failures for other usernames behind the same IP.
        usernameIpAttempts.remove(KEY_PREFIX_USERNAME_IP + normalizedIp + ":" + normalizedUsername);
    }

    private boolean consume(
            ConcurrentMap<String, AttemptWindow> windows,
            String key,
            int maxAttempts,
            Duration window,
            Instant now) {

        AttemptWindow attemptWindow = windows.computeIfAbsent(key, ignored -> new AttemptWindow());
        boolean allowed = attemptWindow.tryConsume(now, maxAttempts, window);
        if (attemptWindow.isEmpty()) {
            windows.remove(key, attemptWindow);
        }
        return allowed;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }

    private String fingerprint(String value) {
        return securityAuditLogger.hashValue(value);
    }

    private static final class AttemptWindow {

        private final Deque<Instant> attempts = new ArrayDeque<>();

        synchronized boolean tryConsume(Instant now, int maxAttempts, Duration window) {
            prune(now.minus(window));
            if (attempts.size() >= maxAttempts) {
                return false;
            }

            attempts.addLast(now);
            return true;
        }

        synchronized boolean isEmpty() {
            return attempts.isEmpty();
        }

        private void prune(Instant cutoff) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.removeFirst();
            }
        }
    }
}

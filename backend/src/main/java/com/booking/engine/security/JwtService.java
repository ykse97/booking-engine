package com.booking.engine.security;

import com.booking.engine.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Service for JWT generation and validation.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Component
public class JwtService {

    public static final String TOKEN_VERSION_CLAIM = "ver";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = buildSigningKey(jwtProperties.getSecret());
    }

    /**
     * Generates signed JWT access token for authenticated user.
     *
     * @param userDetails authenticated principal
     * @param claims additional token claims
     * @return JWT string
     */
    public String generateToken(UserDetails userDetails, Map<String, Object> claims) {
        return buildToken(userDetails, claims);
    }

    /**
     * Generates signed JWT access token that also carries the current admin token
     * version for server-side invalidation.
     *
     * @param userDetails authenticated principal
     * @param claims additional token claims
     * @param tokenVersion current persisted token version for this admin
     * @return JWT string
     */
    public String generateToken(UserDetails userDetails, Map<String, Object> claims, int tokenVersion) {
        Map<String, Object> tokenClaims = new LinkedHashMap<>(claims);
        tokenClaims.put(TOKEN_VERSION_CLAIM, tokenVersion);
        return buildToken(userDetails, tokenClaims);
    }

    /**
     * Extracts token version from token claims.
     *
     * @param token JWT token
     * @return token version or {@code null} when absent
     */
    public Integer extractTokenVersion(String token) {
        return extractClaims(token).get(TOKEN_VERSION_CLAIM, Integer.class);
    }

    /**
     * Validates token against user, expiration, and the expected persisted token
     * version.
     *
     * @param token token string
     * @param userDetails principal
     * @param expectedTokenVersion current persisted token version
     * @return true if valid
     */
    public boolean isTokenValid(String token, UserDetails userDetails, int expectedTokenVersion) {
        Claims claims = extractClaims(token);
        String username = claims.getSubject();
        Date expiration = claims.getExpiration();
        Integer tokenVersion = claims.get(TOKEN_VERSION_CLAIM, Integer.class);

        return username != null
                && username.equals(userDetails.getUsername())
                && expiration != null
                && expiration.after(new Date())
                && tokenVersion != null
                && tokenVersion == expectedTokenVersion;
    }

    /**
     * Extracts username (subject) from token.
     *
     * @param token JWT token
     * @return username
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Validates token against user and expiration.
     *
     * @param token token string
     * @param userDetails principal
     * @return true if valid
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractClaims(token);
        String username = claims.getSubject();
        Date expiration = claims.getExpiration();

        return username != null
                && username.equals(userDetails.getUsername())
                && expiration != null
                && expiration.after(new Date());
    }

    private String buildToken(UserDetails userDetails, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getExpirationSeconds());

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /*
     * Parses and validates signed JWT claims.
     *
     * @param token JWT string
     * @return parsed claims
     */
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /*
     * Builds the HMAC signing key from the configured secret, accepting either a
     * Base64-encoded value or a raw UTF-8 string as fallback.
     *
     * @return secret key used for JWT signing and verification
     */
    private SecretKey buildSigningKey(String secret) {
        try {
            byte[] keyBytes;
            try {
                keyBytes = Decoders.BASE64.decode(secret);
            } catch (Exception ignored) {
                keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (WeakKeyException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid JWT secret configuration. Configure at least 256 bits after Base64 decoding or UTF-8 encoding.",
                    ex);
        }
    }
}

package com.booking.engine.config;

import com.booking.engine.exception.ErrorResponse;
import com.booking.engine.security.AuthSecurityMessages;
import com.booking.engine.security.AdminUserDetailsService;
import com.booking.engine.security.JwtAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Security configuration with JWT-based stateless authentication.
 * Public endpoints are open.
 * ADMIN has full access to admin endpoints.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String API_PUBLIC = "/api/v1/public/**";
    private static final String API_ADMIN = "/api/v1/admin/**";
    private static final String STRIPE_WEBHOOK = "/webhook";
    private static final String ACTUATOR_HEALTH = "/actuator/health";
    private static final String ACTUATOR_HEALTH_NESTED = "/actuator/health/**";
    private static final String ACTUATOR_INFO = "/actuator/info";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'";
    private static final String API_PERMISSIONS_POLICY =
            "camera=(), geolocation=(), microphone=(), usb=()";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminUserDetailsService adminUserDetailsService;
    private final JsonMapper jsonMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AdminUserDetailsService adminUserDetailsService,
            JsonMapper jsonMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.adminUserDetailsService = adminUserDetailsService;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Configures security filter chain.
     * Public endpoints are open, admin endpoints are role-restricted.
     *
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(API_CONTENT_SECURITY_POLICY));
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.frameOptions(frame -> frame.deny());
                    headers.referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER));
                    headers.permissionsPolicyHeader(permissions -> permissions
                            .policy(API_PERMISSIONS_POLICY));
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .maxAgeInSeconds(31536000)
                            .includeSubDomains(true));
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, API_PUBLIC).permitAll()
                        .requestMatchers(API_PUBLIC).permitAll()
                        .requestMatchers(HttpMethod.GET, ACTUATOR_HEALTH).permitAll()
                        .requestMatchers(HttpMethod.GET, ACTUATOR_HEALTH_NESTED).permitAll()
                        .requestMatchers(HttpMethod.GET, ACTUATOR_INFO).permitAll()
                        .requestMatchers(API_ADMIN).hasRole(ROLE_ADMIN)
                        .requestMatchers(HttpMethod.POST, STRIPE_WEBHOOK).permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                (req, res, e) -> writeErrorResponse(
                                        res,
                                        HttpStatus.UNAUTHORIZED,
                                        "Unauthorized",
                                        AuthSecurityMessages.AUTHENTICATION_FAILED,
                                        req.getRequestURI()))
                        .accessDeniedHandler(
                                (req, res, e) -> writeErrorResponse(
                                        res,
                                        HttpStatus.FORBIDDEN,
                                        "Forbidden",
                                        "Forbidden",
                                        req.getRequestURI())));

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Authentication provider based on admin users from database.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Password encoder used for admin password hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication manager used by login service.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            String error,
            String message,
            String path) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                error,
                message,
                path));
    }
}

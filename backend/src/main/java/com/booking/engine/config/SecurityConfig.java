package com.booking.engine.config;

import com.booking.engine.security.AdminUserDetailsService;
import com.booking.engine.security.JwtAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletResponse;

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
public class SecurityConfig {

    private static final String API_PUBLIC = "/api/v1/public/**";
    private static final String API_ADMIN = "/api/v1/admin/**";
    private static final String STRIPE_WEBHOOK = "/webhook";
    private static final String ACTUATOR_HEALTH = "/actuator/health";
    private static final String ACTUATOR_HEALTH_NESTED = "/actuator/health/**";
    private static final String ACTUATOR_INFO = "/actuator/info";
    private static final String ROLE_ADMIN = "ADMIN";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminUserDetailsService adminUserDetailsService;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AdminUserDetailsService adminUserDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.adminUserDetailsService = adminUserDetailsService;
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
                                (req, res, e) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                        .accessDeniedHandler(
                                (req, res, e) -> res.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")));

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
}

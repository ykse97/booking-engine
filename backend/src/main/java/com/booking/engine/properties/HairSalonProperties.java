package com.booking.engine.properties;

import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Configuration properties for hair salon settings.
 * Validated on startup.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.hair-salon")
public class HairSalonProperties {

    /** Default hair salon ID (singleton pattern). */
    @NotNull(message = "Hair salon ID must be configured")
    private UUID id;
}

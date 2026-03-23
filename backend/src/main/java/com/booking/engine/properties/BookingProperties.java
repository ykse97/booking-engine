package com.booking.engine.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for booking lifecycle logic.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.booking")
public class BookingProperties {

    @NotBlank(message = "Booking timezone must be configured")
    private String timezone;
}


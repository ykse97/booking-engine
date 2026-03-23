package com.booking.engine.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Stripe integration.
 *
 * @author Yehor
 * @version 1.0
 * @since March 2026
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    @NotBlank(message = "Stripe secret key must be configured")
    private String secretKey;

    @NotBlank(message = "Stripe webhook secret must be configured")
    private String webhookSecret;

    @NotBlank(message = "Stripe currency must be configured")
    private String currency;

    private String publishableKey;
}

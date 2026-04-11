package com.booking.engine.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

/**
 * Configuration for securely bootstrapping the initial admin account in
 * non-local environments.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.admin.bootstrap")
public class AdminBootstrapProperties {

    private boolean enabled;
    private boolean allowPasswordOverwrite;
    private String username;
    private String password;
}

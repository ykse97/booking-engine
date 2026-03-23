package com.booking.engine.config;

import com.booking.engine.entity.AdminRole;
import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.properties.AdminBootstrapProperties;
import com.booking.engine.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces the insecure seeded admin password with an environment-provided one
 * for cloud deployments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        String username = normalize(properties.getUsername());
        String password = properties.getPassword();

        if (username == null || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Admin bootstrap is enabled, but APP_ADMIN_BOOTSTRAP_USERNAME "
                            + "or APP_ADMIN_BOOTSTRAP_PASSWORD is missing.");
        }

        AdminUserEntity adminUser = adminUserRepository.findByUsername(username)
                .orElseGet(() -> AdminUserEntity.builder()
                        .username(username)
                        .role(AdminRole.ADMIN)
                        .active(true)
                        .build());

        boolean created = adminUser.getId() == null;
        boolean changed = created;

        if (adminUser.getRole() != AdminRole.ADMIN) {
            adminUser.setRole(AdminRole.ADMIN);
            changed = true;
        }

        if (!Boolean.TRUE.equals(adminUser.getActive())) {
            adminUser.setActive(true);
            changed = true;
        }

        if (adminUser.getPasswordHash() == null || !passwordEncoder.matches(password, adminUser.getPasswordHash())) {
            adminUser.setPasswordHash(passwordEncoder.encode(password));
            changed = true;
        }

        if (changed) {
            adminUserRepository.save(adminUser);
            log.info("Admin bootstrap {} for username={}", created ? "created" : "updated", username);
        } else {
            log.info("Admin bootstrap verified existing credentials for username={}", username);
        }
    }

    /*
     * Trims a bootstrap property value and converts blank input to {@code null}
     * so startup validation can treat empty and missing values consistently.
     *
     * @param value raw property value
     * @return normalized value or {@code null} when blank
     */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

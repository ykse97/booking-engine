package com.booking.engine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.engine.entity.AdminRole;
import com.booking.engine.entity.AdminUserEntity;
import com.booking.engine.properties.AdminBootstrapProperties;
import com.booking.engine.repository.AdminUserRepository;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock
    private AdminBootstrapProperties properties;

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminBootstrapInitializer initializer;

    @Test
    void runDoesNothingWhenBootstrapIsDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(adminUserRepository, never()).findByUsername(any());
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void runFailsFastWhenRequiredCredentialsAreMissing() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn("   ");
        when(properties.getPassword()).thenReturn(" ");

        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ADMIN_BOOTSTRAP_USERNAME")
                .hasMessageContaining("APP_ADMIN_BOOTSTRAP_PASSWORD");
    }

    @Test
    void runCreatesAdminUserWhenItDoesNotExist() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn(" admin ");
        when(properties.getPassword()).thenReturn("secret");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hashed-secret");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<AdminUserEntity> savedUser = ArgumentCaptor.forClass(AdminUserEntity.class);
        verify(adminUserRepository).save(savedUser.capture());

        assertThat(savedUser.getValue().getUsername()).isEqualTo("admin");
        assertThat(savedUser.getValue().getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(savedUser.getValue().getActive()).isTrue();
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-secret");
    }

    @Test
    void runUpdatesExistingUserWhenRoleStatusOrPasswordNeedCorrection() {
        AdminUserEntity existingUser = AdminUserEntity.builder()
                .username("admin")
                .role(null)
                .active(false)
                .passwordHash("old-hash")
                .build();
        existingUser.setId(UUID.randomUUID());

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn("admin");
        when(properties.getPassword()).thenReturn("new-secret");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("new-secret", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(adminUserRepository).save(existingUser);
        assertThat(existingUser.getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(existingUser.getActive()).isTrue();
        assertThat(existingUser.getPasswordHash()).isEqualTo("new-hash");
    }

    @Test
    void runDoesNotPersistWhenExistingAdminCredentialsAreAlreadyValid() {
        AdminUserEntity existingUser = AdminUserEntity.builder()
                .username("admin")
                .role(AdminRole.ADMIN)
                .active(true)
                .passwordHash("stored-hash")
                .build();
        existingUser.setId(UUID.randomUUID());

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getUsername()).thenReturn("admin");
        when(properties.getPassword()).thenReturn("secret");
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        verify(adminUserRepository, never()).save(any());
    }
}

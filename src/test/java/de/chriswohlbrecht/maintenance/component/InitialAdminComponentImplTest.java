package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.InitialAdminProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialAdminComponentImplTest {

    private static final String ADMIN_EMAIL = "admin@localhost";

    @Mock
    private AppUserRepository appUserRepository;

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void initializePassword_adminWithoutPassword_setsHashedPassword() {
        AppUser admin = admin(null);
        when(appUserRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        component("correct horse battery").initializePassword();

        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("correct horse battery", admin.getPasswordHash())).isTrue();
        verify(appUserRepository).save(admin);
    }

    @Test
    void initializePassword_adminWithPassword_keepsExistingPassword() {
        AppUser admin = admin("{bcrypt}existing");
        when(appUserRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        component("correct horse battery").initializePassword();

        assertThat(admin.getPasswordHash()).isEqualTo("{bcrypt}existing");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void initializePassword_noPasswordConfigured_leavesAccountWithoutPassword() {
        AppUser admin = admin(null);
        when(appUserRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin));

        component("").initializePassword();

        assertThat(admin.getPasswordHash()).isNull();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void initializePassword_adminMissing_doesNothing() {
        when(appUserRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.empty());

        component("correct horse battery").initializePassword();

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void initializePassword_tooShort_fails() {
        when(appUserRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin(null)));

        assertThatThrownBy(() -> component("short").initializePassword())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 10");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void initializePassword_moreThan72Bytes_fails() {
        when(appUserRepository.findByEmailIgnoreCase(ADMIN_EMAIL)).thenReturn(Optional.of(admin(null)));
        // 37 umlauts = 37 characters but 74 bytes in UTF-8
        String password = "ä".repeat(37);

        assertThatThrownBy(() -> component(password).initializePassword())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72 bytes");
        verify(appUserRepository, never()).save(any());
    }

    private InitialAdminComponentImpl component(String configuredPassword) {
        return new InitialAdminComponentImpl(appUserRepository, passwordEncoder,
                new InitialAdminProperties(ADMIN_EMAIL, configuredPassword));
    }

    private static AppUser admin(String passwordHash) {
        return AppUser.builder()
                .id(1L)
                .email(ADMIN_EMAIL)
                .role(UserRole.ADMIN)
                .emailVerified(true)
                .passwordHash(passwordHash)
                .build();
    }
}

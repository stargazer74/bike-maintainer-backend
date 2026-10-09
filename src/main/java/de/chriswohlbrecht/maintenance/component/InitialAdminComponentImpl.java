package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.InitialAdminProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitialAdminComponentImpl implements IInitialAdminComponent {

    static final int MIN_PASSWORD_LENGTH = 10;
    /** BCrypt only uses the first 72 bytes of the input. */
    static final int MAX_PASSWORD_BYTES = 72;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final InitialAdminProperties initialAdminProperties;

    @Override
    @Transactional
    public void initializePassword() {
        Optional<AppUser> initialAdmin = appUserRepository.findByEmailIgnoreCase(initialAdminProperties.email().trim());
        if (initialAdmin.isEmpty()) {
            log.warn("Initial admin {} not found; BM_INITIAL_ADMIN_EMAIL is only applied by the first migration run",
                    initialAdminProperties.email());
            return;
        }
        AppUser admin = initialAdmin.get();
        if (admin.getPasswordHash() != null) {
            return;
        }
        String password = initialAdminProperties.password();
        if (password == null || password.isBlank()) {
            log.warn("Initial admin {} has no password and BM_INITIAL_ADMIN_PASSWORD is not set; "
                    + "the account cannot log in until it is set", admin.getEmail());
            return;
        }
        validate(password);
        admin.setPasswordHash(passwordEncoder.encode(password));
        appUserRepository.save(admin);
        log.info("Initial password set for admin {}", admin.getEmail());
    }

    private static void validate(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "BM_INITIAL_ADMIN_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException(
                    "BM_INITIAL_ADMIN_PASSWORD must not exceed " + MAX_PASSWORD_BYTES + " bytes (UTF-8)");
        }
    }
}

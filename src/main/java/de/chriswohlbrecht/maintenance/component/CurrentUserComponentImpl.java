package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.InitialAdminProperties;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Interim implementation until login exists (backend#5): every request acts as the initial admin, so new
 * vehicles get a valid owner. backend#5 replaces this with the authenticated user from the security context.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserComponentImpl implements ICurrentUserComponent {

    private final AppUserRepository appUserRepository;
    private final InitialAdminProperties initialAdminProperties;

    @Override
    public AppUser getCurrentUser() {
        return appUserRepository.findByEmailIgnoreCase(initialAdminProperties.email().trim())
                .orElseThrow(() -> new IllegalStateException(
                        "Initial admin " + initialAdminProperties.email() + " not found"));
    }
}

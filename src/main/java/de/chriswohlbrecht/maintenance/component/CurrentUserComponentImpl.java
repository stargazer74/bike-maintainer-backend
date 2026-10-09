package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.SessionUser;
import de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the logged-in user from the security context (session). */
@Component
@RequiredArgsConstructor
public class CurrentUserComponentImpl implements ICurrentUserComponent {

    private final AppUserRepository appUserRepository;

    @Override
    public AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionUser sessionUser)) {
            throw new AuthenticationFailedException("Not logged in");
        }
        // The account may have been deleted while the session was still alive.
        return appUserRepository.findById(sessionUser.id())
                .orElseThrow(() -> new AuthenticationFailedException("Not logged in"));
    }
}

package de.chriswohlbrecht.maintenance.component.model;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;

/**
 * Principal stored in the HTTP session after login. Kept minimal because it is serialized into the
 * session table; the full user is loaded from the database when needed.
 *
 * <p>{@link #getName()} returns the user id, which Spring Session indexes so that all sessions of a user can
 * be found and invalidated (e.g. after a password change or deactivation).
 */
public record SessionUser(Long id, String email) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Authentication stored in the session after a login (portal) or a profile selection (household). */
    public static Authentication authenticationFor(AppUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(new SessionUser(user.getId(), user.getEmail()), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }
}

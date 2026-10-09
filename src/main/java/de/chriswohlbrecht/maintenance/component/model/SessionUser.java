package de.chriswohlbrecht.maintenance.component.model;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

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

    @Override
    public String getName() {
        return String.valueOf(id);
    }
}

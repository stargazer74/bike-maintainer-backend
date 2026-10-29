package de.chriswohlbrecht.maintenance.component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

public interface ISessionComponent {

    /**
     * Stores the authentication in a new session (new session id against session fixation, fresh CSRF token),
     * as Spring's own login filters do. Used by the login (portal) and the profile selection (household).
     */
    void startSession(Authentication authentication, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /** Deletes all sessions of the user, e.g. after the user has been deleted. */
    void invalidateSessionsOf(Long userId);

    /** Deletes all sessions of the user except the given one, e.g. after the user changed the password. */
    void invalidateOtherSessionsOf(Long userId, String keptSessionId);
}

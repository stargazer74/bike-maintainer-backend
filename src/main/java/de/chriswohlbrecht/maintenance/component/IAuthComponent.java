package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.LoginRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IAuthComponent {

    /**
     * Verifies the credentials and stores the login in the (new) session.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException wrong email or password
     * @throws de.chriswohlbrecht.maintenance.exception.AccountUnusableException     deactivated or unverified
     * @throws de.chriswohlbrecht.maintenance.exception.TooManyRequestsException     account temporarily locked
     */
    UserResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /** Invalidates the session and the CSRF token. */
    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    UserResponse getCurrentUser();
}

package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.LoginRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException;
import de.chriswohlbrecht.maintenance.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthComponentImpl implements IAuthComponent {

    private final AuthenticationManager authenticationManager;
    private final ISessionComponent sessionComponent;
    private final CsrfTokenRepository csrfTokenRepository;
    private final ICurrentUserComponent currentUserComponent;
    private final UserMapper userMapper;

    @Override
    public UserResponse login(LoginRequest request, HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException e) {
            throw new AuthenticationFailedException(e.getMessage());
        }

        sessionComponent.startSession(authentication, httpRequest, httpResponse);
        return userMapper.toResponse(currentUserComponent.getCurrentUser());
    }

    /** Spring Session expires the session cookie itself when the session is invalidated. */
    @Override
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new CsrfLogoutHandler(csrfTokenRepository).logout(httpRequest, httpResponse, authentication);
        new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
    }

    @Override
    public UserResponse getCurrentUser() {
        return userMapper.toResponse(currentUserComponent.getCurrentUser());
    }
}

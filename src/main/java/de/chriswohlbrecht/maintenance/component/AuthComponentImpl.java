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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthComponentImpl implements IAuthComponent {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
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

        // New session id (session fixation) and a fresh CSRF token, as Spring's own login filters do.
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        renderCsrfToken(httpRequest);

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

    /**
     * The CSRF token is loaded lazily; after it has been rotated, reading it makes Spring write the new
     * XSRF-TOKEN cookie in this response, so the client can send state-changing requests right away.
     */
    private static void renderCsrfToken(HttpServletRequest httpRequest) {
        if (httpRequest.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
    }
}

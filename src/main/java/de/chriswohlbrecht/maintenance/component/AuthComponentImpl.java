package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.LoginRequest;
import de.chriswohlbrecht.maintenance.api.model.UpdateCurrentUserRequest;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.exception.AuthenticationFailedException;
import de.chriswohlbrecht.maintenance.exception.InvalidUserUpdateException;
import de.chriswohlbrecht.maintenance.mapper.UserMapper;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.repository.AppUserRepository;
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
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuthComponentImpl implements IAuthComponent {

    private final AuthenticationManager authenticationManager;
    private final ISessionComponent sessionComponent;
    private final CsrfTokenRepository csrfTokenRepository;
    private final ICurrentUserComponent currentUserComponent;
    private final UserMapper userMapper;
    private final AppUserRepository appUserRepository;

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

    @Override
    @Transactional
    public UserResponse updateCurrentUser(UpdateCurrentUserRequest request) {
        Language language = request.getLanguage();
        Currency currency = request.getCurrency();
        if (language == null && currency == null) {
            throw new InvalidUserUpdateException("Either language or currency must be given");
        }
        AppUser user = currentUserComponent.getCurrentUser();
        if (language != null) {
            user.setLanguage(language.getValue());
        }
        if (currency != null) {
            user.setCurrency(currency.getValue());
        }
        return userMapper.toResponse(appUserRepository.save(user));
    }
}

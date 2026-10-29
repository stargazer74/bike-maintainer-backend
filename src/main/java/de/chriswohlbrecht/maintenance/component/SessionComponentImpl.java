package de.chriswohlbrecht.maintenance.component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionComponentImpl implements ISessionComponent {

    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Override
    public void startSession(Authentication authentication, HttpServletRequest httpRequest,
                             HttpServletResponse httpResponse) {
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        renderCsrfToken(httpRequest);
    }

    /** Sessions are indexed by the principal name, which is the user id (see {@code SessionUser#getName()}). */
    @Override
    public void invalidateSessionsOf(Long userId) {
        sessionRepository.findByPrincipalName(String.valueOf(userId)).keySet()
                .forEach(sessionRepository::deleteById);
    }

    @Override
    public void invalidateOtherSessionsOf(Long userId, String keptSessionId) {
        sessionRepository.findByPrincipalName(String.valueOf(userId)).keySet().stream()
                .filter(sessionId -> !sessionId.equals(keptSessionId))
                .forEach(sessionRepository::deleteById);
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

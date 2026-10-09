package de.chriswohlbrecht.maintenance.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Loads the (deferred) CSRF token on every request so that the XSRF-TOKEN cookie is always issued — the SPA
 * needs it before its first state-changing request, e.g. the login. {@code csrf.spa()} does not do this by
 * itself. Not a Spring bean, for the same reason as {@link RateLimitFilter}.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
        chain.doFilter(request, response);
    }
}

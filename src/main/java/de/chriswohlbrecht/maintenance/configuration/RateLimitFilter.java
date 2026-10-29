package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.component.IRateLimitComponent;
import de.chriswohlbrecht.maintenance.exception.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Limits public, brute-forceable auth endpoints per client IP. Runs before CSRF and authentication so that
 * every attempt counts. The client IP is {@link HttpServletRequest#getRemoteAddr()}, which Tomcat's
 * RemoteIpValve (forward-headers-strategy=native) derives from X-Forwarded-For only for trusted internal
 * proxies.
 *
 * <p>Not a Spring bean on purpose: as a bean, Spring Boot would also register it as a plain servlet filter.
 */
class RateLimitFilter extends OncePerRequestFilter {

    /** Method and path; changing the password needs a session, but the current password can be guessed there. */
    static final Set<String> LIMITED_REQUESTS = Set.of(
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/verify-email/resend",
            "POST /api/v1/auth/password-reset/request",
            "PUT /api/v1/auth/password");

    private final IRateLimitComponent rateLimitComponent;
    private final SecurityErrorWriter errorWriter;

    RateLimitFilter(IRateLimitComponent rateLimitComponent, SecurityErrorWriter errorWriter) {
        this.rateLimitComponent = rateLimitComponent;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_REQUESTS.contains(request.getMethod() + " " + decodedPath(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> retryAfter = rateLimitComponent.tryConsume(
                decodedPath(request) + "|" + request.getRemoteAddr());
        if (retryAfter.isPresent()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ErrorResponses.retryAfterSeconds(retryAfter.get())));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The path as Spring MVC matches it: percent-decoded, without ";…" parameters. The raw request URI would let
     * {@code /api/v1/auth/logi%6E} reach the login endpoint without being limited.
     */
    private static String decodedPath(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}

package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.component.IRateLimitComponent;
import de.chriswohlbrecht.maintenance.exception.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Limits public, brute-forceable auth endpoints per client IP. Runs before CSRF and authentication so that
 * every attempt counts; successful logins are taken back afterwards. The client IP is
 * {@link HttpServletRequest#getRemoteAddr()}, which Tomcat's RemoteIpValve (forward-headers-strategy=native)
 * derives from X-Forwarded-For only for trusted internal proxies.
 *
 * <p>Not a Spring bean on purpose: as a bean, Spring Boot would also register it as a plain servlet filter.
 */
class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Limited request (method and path) → its group, the tag of {@code bm.protection.rate_limited}. Changing the
     * password and deleting the account need a session, but the current password can be guessed there.
     */
    static final Map<String, String> LIMITED_REQUESTS = Map.of(
            "POST /api/v1/auth/login", "login",
            "POST /api/v1/auth/register", "registration",
            "POST /api/v1/auth/verify-email/resend", "verification_resend",
            "POST /api/v1/auth/password-reset/request", "password_reset",
            "PUT /api/v1/auth/password", "password_change",
            "DELETE /api/v1/auth/me", "account_deletion",
            "POST /api/v1/auth/device-login", "device_login",
            "POST /api/v1/auth/device-session", "device_session");

    /**
     * Groups whose successful attempts (2xx) are taken back after the request: a successful login is no guess, and
     * counting it locks out a household that logs in and out a few times. Still counted up front, so parallel
     * requests cannot slip past the limit; failed logins also count towards the account lockout.
     */
    static final Set<String> REFUNDED_ON_SUCCESS = Set.of("login", "device_login");

    private final IRateLimitComponent rateLimitComponent;
    private final SecurityErrorWriter errorWriter;

    RateLimitFilter(IRateLimitComponent rateLimitComponent, SecurityErrorWriter errorWriter) {
        this.rateLimitComponent = rateLimitComponent;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_REQUESTS.containsKey(request.getMethod() + " " + decodedPath(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String group = LIMITED_REQUESTS.get(request.getMethod() + " " + decodedPath(request));
        String key = decodedPath(request) + "|" + request.getRemoteAddr();
        Optional<Duration> retryAfter = rateLimitComponent.tryConsume(group, key);
        if (retryAfter.isPresent()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ErrorResponses.retryAfterSeconds(retryAfter.get())));
            errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later");
            return;
        }
        chain.doFilter(request, response);
        if (REFUNDED_ON_SUCCESS.contains(group) && HttpStatusCode.valueOf(response.getStatus()).is2xxSuccessful()) {
            rateLimitComponent.refund(key);
        }
    }

    /**
     * The path as Spring MVC matches it: percent-decoded, without ";…" parameters. The raw request URI would let
     * {@code /api/v1/auth/logi%6E} reach the login endpoint without being limited.
     */
    private static String decodedPath(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}

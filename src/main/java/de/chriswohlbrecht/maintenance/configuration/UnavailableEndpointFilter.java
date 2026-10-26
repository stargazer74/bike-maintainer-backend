package de.chriswohlbrecht.maintenance.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Answers 404 for endpoints that do not exist in the current operating mode, before CSRF and authentication,
 * so that they look like unknown paths regardless of session and CSRF token. Not a Spring bean, for the same
 * reason as {@link RateLimitFilter}.
 */
class UnavailableEndpointFilter extends OncePerRequestFilter {

    private final List<RequestMatcher> unavailable;
    private final SecurityErrorWriter errorWriter;

    UnavailableEndpointFilter(SecurityErrorWriter errorWriter, String... pathPatterns) {
        this.errorWriter = errorWriter;
        this.unavailable = Arrays.stream(pathPatterns)
                .map(pattern -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(pattern))
                .toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return unavailable.stream().noneMatch(matcher -> matcher.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        errorWriter.write(request, response, HttpStatus.NOT_FOUND, "Not available in this operating mode");
    }
}

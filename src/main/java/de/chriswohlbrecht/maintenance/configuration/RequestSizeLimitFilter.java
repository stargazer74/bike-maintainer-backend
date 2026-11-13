package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.exception.PayloadTooLargeException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Limits the body of the vehicle import (Epic #82) to {@link #IMPORT_LIMIT} bytes and of the payment provider's
 * webhook (backend#139, public without login) to {@link #WEBHOOK_LIMIT} bytes, so a huge body is neither buffered nor
 * parsed. A declared {@code Content-Length} above the limit is answered with 413 right away; without one
 * (chunked), the body is counted while it is read and reading ends with a {@link PayloadTooLargeException}, which
 * {@code GlobalExceptionHandler} turns into 413. Not a Spring bean, for the same reason as {@link RateLimitFilter}.
 */
class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long IMPORT_LIMIT = 5L * 1024 * 1024;
    /** Paddle events are a few KB; everything far above is not from Paddle. */
    static final long WEBHOOK_LIMIT = 64L * 1024;

    private static final Map<RequestMatcher, Long> LIMITS = Map.of(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/vehicles/import"), IMPORT_LIMIT,
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/webhooks/paddle"), WEBHOOK_LIMIT);

    private final SecurityErrorWriter errorWriter;

    RequestSizeLimitFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limitOf(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = limitOf(request);
        if (request.getContentLengthLong() > limit) {
            errorWriter.write(request, response, HttpStatus.CONTENT_TOO_LARGE, "Request body too large");
            return;
        }
        chain.doFilter(new LimitedRequest(request, limit), response);
    }

    private static Long limitOf(HttpServletRequest request) {
        return LIMITS.entrySet().stream()
                .filter(entry -> entry.getKey().matches(request))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    /** Hands out a body stream that fails once more than the limit has been read. */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The body stream is meant to be read by the caller")
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }
    }

    static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                counted(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                counted(n);
            }
            return n;
        }

        private void counted(int n) throws PayloadTooLargeException {
            count += n;
            if (count > limit) {
                throw new PayloadTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}

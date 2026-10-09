package de.chriswohlbrecht.maintenance.exception;

import de.chriswohlbrecht.maintenance.api.model.ErrorResponse;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Builds {@link ErrorResponse} bodies consistently for the exception handler and the security filters. */
public final class ErrorResponses {

    private ErrorResponses() {
    }

    public static ErrorResponse of(HttpStatus status, String code, String message, String path) {
        return new ErrorResponse()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(path);
    }

    /** Value for the Retry-After header: whole seconds, rounded up and at least 1, so clients never retry too early. */
    public static long retryAfterSeconds(Duration retryAfter) {
        return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
    }
}

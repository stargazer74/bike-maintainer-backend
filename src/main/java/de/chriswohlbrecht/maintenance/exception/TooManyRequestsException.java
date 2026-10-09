package de.chriswohlbrecht.maintenance.exception;

import lombok.Getter;

import java.time.Duration;

/**
 * Rate limit exceeded or account temporarily locked (429). Both cases deliberately produce the same
 * response so that a lockout does not reveal that the account exists.
 */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super("Too many attempts, try again later");
        this.retryAfter = retryAfter;
    }
}

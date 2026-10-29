package de.chriswohlbrecht.maintenance.exception;

import lombok.Getter;

import java.time.Duration;

/** The daily email limit is reached (429 {@code MAIL_QUOTA_EXCEEDED}); it resets at midnight. */
@Getter
public class MailQuotaExceededException extends RuntimeException {

    public static final String CODE = "MAIL_QUOTA_EXCEEDED";

    private final Duration retryAfter;

    public MailQuotaExceededException(Duration retryAfter) {
        super("Daily email limit reached, try again tomorrow");
        this.retryAfter = retryAfter;
    }
}

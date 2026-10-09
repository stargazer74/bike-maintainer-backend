package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param rateLimitMaxAttempts     requests per client IP and public auth endpoint within {@code rateLimitWindow}
 * @param rateLimitWindow          fixed window for the per-IP rate limit
 * @param lockoutMaxFailedAttempts failed logins after which an account is locked
 * @param lockoutDuration          how long a locked account stays locked
 */
@Validated
@ConfigurationProperties(prefix = "bm.security")
public record SecurityProperties(
        @Positive int rateLimitMaxAttempts,
        @NotNull Duration rateLimitWindow,
        @Positive int lockoutMaxFailedAttempts,
        @NotNull Duration lockoutDuration) {
}

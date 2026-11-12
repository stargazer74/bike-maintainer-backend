package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Period;

/**
 * Deletion of expired accounts (portal mode, backend#140).
 *
 * @param period           how long an account is kept after its access (trial or paid period) ended, or after the
 *                         person logged in in reply to the deletion notice
 * @param noticePeriod     how long before the deletion the notice is sent; never deleted earlier after the notice
 * @param noticeDailyLimit deletion notices per day at most, separate from {@code bm.mail.daily-limit}, so they never
 *                         use up the emails for registrations; the rest follow on the next days
 */
@Validated
@ConfigurationProperties(prefix = "bm.retention")
public record RetentionProperties(
        @NotNull Period period,
        @NotNull Period noticePeriod,
        @Positive int noticeDailyLimit) {
}

package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Early end of subscriptions cancelled after the first year (portal mode, backend#147).
 *
 * @param enabled     end and refund automatically at the provider ({@code BM_EARLY_END_ENABLED}). Off by default: then
 *                    the administrators only get an email and do it by hand (backend#145). Needs the API key permissions
 *                    {@code transaction.read} and {@code adjustment.write} besides {@code subscription.write}
 * @param maxAttempts nightly runs in a row that may fail before the early end is handed over to the administrators
 */
@Validated
@ConfigurationProperties(prefix = "bm.subscription.early-end")
public record EarlyEndProperties(boolean enabled, @Positive int maxAttempts) {
}

package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param trialDays length of the free trial in days (portal mode, {@code BM_SUBSCRIPTION_TRIAL_DAYS}). Deliberately
 *                  without a default: the value is a business decision of the deployment, so portal mode refuses to
 *                  start without it ({@link PortalStartupCheck}); household mode does not need it.
 */
@Validated
@ConfigurationProperties(prefix = "bm.subscription")
public record SubscriptionProperties(@Positive Integer trialDays) {
}

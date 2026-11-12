package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Portal mode refuses to start without the settings registration and the trial need, so a forgotten variable is
 * noticed at deployment and not when the first user registers. Without the profile {@code local} emails are really
 * sent, so an SMTP host is required; {@code local} only logs them.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(OperatingModeCondition.Portal.class)
public final class PortalStartupCheck {

    /** Property, environment variable and what it is, for the subscription via Polar. */
    private static final String[][] POLAR_SETTINGS = {
            {"bm.polar.access-token", "BM_POLAR_ACCESS_TOKEN", "organization access token of Polar"},
            {"bm.polar.webhook-secret", "BM_POLAR_WEBHOOK_SECRET", "secret of the Polar webhook endpoint"},
            {"bm.polar.product-id", "BM_POLAR_PRODUCT_ID", "id of the subscription product in Polar"}
    };

    public PortalStartupCheck(Environment environment) {
        if (isBlank(environment.getProperty("bm.altcha.hmac-key"))) {
            throw new IllegalStateException("BM_ALTCHA_HMAC_KEY must be set in portal mode "
                    + "(secret for the registration captcha, e.g. openssl rand -hex 32)");
        }
        if (isBlank(environment.getProperty("bm.subscription.trial-days"))) {
            throw new IllegalStateException("BM_SUBSCRIPTION_TRIAL_DAYS must be set in portal mode "
                    + "(length of the free trial in days)");
        }
        if (!environment.acceptsProfiles(Profiles.of("local"))) {
            for (String[] polar : POLAR_SETTINGS) {
                if (isBlank(environment.getProperty(polar[0]))) {
                    throw new IllegalStateException(polar[1] + " must be set in portal mode (" + polar[2]
                            + "; the profile local starts without Polar, checkout then answers 503)");
                }
            }
        }
        if (!environment.acceptsProfiles(Profiles.of("local"))
                && isBlank(environment.getProperty("spring.mail.host"))) {
            throw new IllegalStateException("SPRING_MAIL_HOST must be set in portal mode "
                    + "(SMTP server for registration emails; use the profile local to only log emails)");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

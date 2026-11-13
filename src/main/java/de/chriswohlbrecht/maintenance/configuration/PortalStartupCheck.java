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

    /** Property, environment variable and what it is, for the subscription via Paddle. */
    private static final String[][] PADDLE_SETTINGS = {
            {"bm.paddle.api-key", "BM_PADDLE_API_KEY", "API key of Paddle"},
            {"bm.paddle.webhook-secret", "BM_PADDLE_WEBHOOK_SECRET", "secret key of the Paddle notification destination"},
            {"bm.paddle.price-id", "BM_PADDLE_PRICE_ID", "id of the yearly price in Paddle"},
            {"bm.paddle.client-token", "BM_PADDLE_CLIENT_TOKEN", "client-side token of Paddle for the payment page"}
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
            for (String[] paddle : PADDLE_SETTINGS) {
                if (isBlank(environment.getProperty(paddle[0]))) {
                    throw new IllegalStateException(paddle[1] + " must be set in portal mode (" + paddle[2]
                            + "; the profile local starts without Paddle, checkout then answers 503)");
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

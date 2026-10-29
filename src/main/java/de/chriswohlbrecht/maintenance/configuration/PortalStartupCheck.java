package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Portal mode refuses to start without the settings registration needs, so a forgotten variable is noticed at
 * deployment and not when the first user registers. Without the profile {@code local} emails are really sent,
 * so an SMTP host is required; {@code local} only logs them.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(OperatingModeCondition.Portal.class)
public final class PortalStartupCheck {

    public PortalStartupCheck(Environment environment) {
        if (isBlank(environment.getProperty("bm.altcha.hmac-key"))) {
            throw new IllegalStateException("BM_ALTCHA_HMAC_KEY must be set in portal mode "
                    + "(secret for the registration captcha, e.g. openssl rand -hex 32)");
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

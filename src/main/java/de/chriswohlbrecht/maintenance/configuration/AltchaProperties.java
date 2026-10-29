package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param hmacKey  secret for signing challenges ({@code BM_ALTCHA_HMAC_KEY}); required in portal mode, see
 *                 {@link PortalStartupCheck}
 * @param cost     PBKDF2 iterations per attempt – the work a browser has to do
 * @param validity how long a challenge can be solved and submitted
 */
@Validated
@ConfigurationProperties(prefix = "bm.altcha")
public record AltchaProperties(
        String hmacKey,
        @Positive int cost,
        @NotNull Duration validity) {
}

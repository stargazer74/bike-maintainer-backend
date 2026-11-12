package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection to the payment provider Polar (portal mode). Token, webhook secret and product are required in portal
 * mode outside the profile {@code local} ({@link PortalStartupCheck}); household mode needs none of them.
 *
 * @param apiUrl        {@code https://api.polar.sh}, or {@code https://sandbox-api.polar.sh} for tests
 *                      ({@code BM_POLAR_API_URL})
 * @param accessToken   organization access token ({@code BM_POLAR_ACCESS_TOKEN})
 * @param webhookSecret {@code whsec_…} secret of the webhook endpoint ({@code BM_POLAR_WEBHOOK_SECRET})
 * @param productId     id of the yearly subscription product ({@code BM_POLAR_PRODUCT_ID})
 */
@Validated
@ConfigurationProperties(prefix = "bm.polar")
public record PolarProperties(@NotBlank String apiUrl, String accessToken, String webhookSecret, String productId) {

    public boolean isConfigured() {
        return isSet(accessToken) && isSet(webhookSecret) && isSet(productId);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}

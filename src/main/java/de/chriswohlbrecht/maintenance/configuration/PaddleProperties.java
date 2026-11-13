package de.chriswohlbrecht.maintenance.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection to the payment provider Paddle (portal mode). Key, webhook secret, price and client token are required in
 * portal mode outside the profile {@code local} ({@link PortalStartupCheck}); household mode needs none of them.
 *
 * @param apiUrl        {@code https://api.paddle.com}, or {@code https://sandbox-api.paddle.com} for tests
 *                      ({@code BM_PADDLE_API_URL})
 * @param apiKey        API key ({@code BM_PADDLE_API_KEY})
 * @param webhookSecret {@code pdl_ntfset_…} secret key of the notification destination
 *                      ({@code BM_PADDLE_WEBHOOK_SECRET})
 * @param priceId       id of the yearly price ({@code BM_PADDLE_PRICE_ID})
 * @param clientToken   public client-side token for Paddle.js on the payment page ({@code BM_PADDLE_CLIENT_TOKEN})
 */
@Validated
@ConfigurationProperties(prefix = "bm.paddle")
public record PaddleProperties(@NotBlank String apiUrl, String apiKey, String webhookSecret, String priceId,
                               String clientToken) {

    /** True if the service talks to Paddle's sandbox; Paddle.js then has to run in the sandbox environment too. */
    public boolean sandbox() {
        return apiUrl.contains("sandbox");
    }
}

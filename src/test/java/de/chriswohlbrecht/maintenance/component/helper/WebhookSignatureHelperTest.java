package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.exception.WebhookSignatureException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureHelperTest {

    private static final String SECRET = "whsec_" + Base64.getEncoder()
            .encodeToString("a-secret-of-the-endpoint".getBytes(StandardCharsets.UTF_8));
    private static final Instant NOW = Instant.ofEpochSecond(1_800_000_000L);
    private static final String TIMESTAMP = String.valueOf(NOW.getEpochSecond());
    private static final String BODY = "{\"type\":\"subscription.active\",\"data\":{\"id\":\"sub_1\"}}";

    /** Example of the Standard Webhooks specification, so the scheme is checked against an outside reference. */
    @Test
    void acceptsTheExampleOfTheSpecification() {
        String secret = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
        String body = "{\"test\": 2432232314}";

        assertThat(WebhookSignatureHelper.signature(secret, "msg_p5jXN8AQM9LWM0D4loKWxJek", "1614265330", body))
                .isEqualTo("v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=");
        assertThatCode(() -> WebhookSignatureHelper.verify(secret, "msg_p5jXN8AQM9LWM0D4loKWxJek", "1614265330",
                "v1,g0hM9SsE+OTPJTGt/tmIKtSyZlE3uFJELVlNIOLJ1OE=", body, Instant.ofEpochSecond(1614265330L)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsOneMatchingSignatureAmongSeveral() {
        String header = "v1,d3Jvbmc= v2,ignored " + WebhookSignatureHelper.signature(SECRET, "msg_1", TIMESTAMP, BODY);

        assertThatCode(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", TIMESTAMP, header, BODY, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAChangedBody() {
        String header = WebhookSignatureHelper.signature(SECRET, "msg_1", TIMESTAMP, BODY);

        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", TIMESTAMP, header, BODY + " ", NOW))
                .isInstanceOf(WebhookSignatureException.class);
    }

    @Test
    void rejectsAnotherSecret() {
        String other = "whsec_" + Base64.getEncoder().encodeToString("other".getBytes(StandardCharsets.UTF_8));
        String header = WebhookSignatureHelper.signature(other, "msg_1", TIMESTAMP, BODY);

        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", TIMESTAMP, header, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class);
    }

    @Test
    void rejectsAReplayOutsideTheTolerance() {
        String old = String.valueOf(NOW.minus(WebhookSignatureHelper.TOLERANCE).getEpochSecond() - 1);
        String header = WebhookSignatureHelper.signature(SECRET, "msg_1", old, BODY);

        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", old, header, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("tolerance");
    }

    @Test
    void rejectsMissingHeadersAndInvalidValues() {
        String header = WebhookSignatureHelper.signature(SECRET, "msg_1", TIMESTAMP, BODY);

        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, null, TIMESTAMP, header, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class);
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", " ", header, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class);
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", TIMESTAMP, "", BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class);
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", TIMESTAMP, header, null, NOW))
                .isInstanceOf(WebhookSignatureException.class);
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", "yesterday", header, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class);
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "msg_1", TIMESTAMP, "v1,%%%", BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class);
    }

    @Test
    void anInvalidSecretIsAConfigurationError() {
        assertThatThrownBy(() -> WebhookSignatureHelper.signature("whsec_%%%", "msg_1", TIMESTAMP, BODY))
                .isInstanceOf(IllegalStateException.class);
    }
}

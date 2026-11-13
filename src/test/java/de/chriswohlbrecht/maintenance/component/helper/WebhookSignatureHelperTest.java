package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.exception.WebhookSignatureException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureHelperTest {

    private static final String SECRET = "pdl_ntfset_01gkpjp8bkm3tm53kdgkx6sms7_6h3qd3uFSi9YCD3OLYAShQI90XTI32a2";
    private static final long TIMESTAMP = 1_671_552_777L;
    private static final Instant NOW = Instant.ofEpochSecond(TIMESTAMP);
    private static final String BODY =
            "{\"event_id\":\"evt_01\",\"event_type\":\"subscription.activated\",\"data\":{\"id\":\"sub_1\"}}";
    /** Computed outside of Java: printf '%s' "$ts:$body" | openssl dgst -sha256 -hmac "$secret" -hex. */
    private static final String EXPECTED = "976450f182f56fe8cddc9d50fe45ae4554281f449fb75aed2ac5158d2f86aadf";

    @Test
    void signsLikeAnOutsideReference() {
        assertThat(WebhookSignatureHelper.signatureHeader(SECRET, TIMESTAMP, BODY))
                .isEqualTo("ts=" + TIMESTAMP + ";h1=" + EXPECTED);
        assertThatCode(() -> WebhookSignatureHelper.verify(SECRET, "ts=" + TIMESTAMP + ";h1=" + EXPECTED, BODY, NOW))
                .doesNotThrowAnyException();
    }

    /** While the secret is rotated, Paddle sends one h1 per secret. */
    @Test
    void acceptsOneMatchingSignatureAmongSeveral() {
        String header = "ts=" + TIMESTAMP + ";h1=" + "ab".repeat(32) + ";h1=" + EXPECTED + ";h2=ignored";

        assertThatCode(() -> WebhookSignatureHelper.verify(SECRET, header, BODY, NOW)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAChangedBody() {
        String header = WebhookSignatureHelper.signatureHeader(SECRET, TIMESTAMP, BODY);

        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, header, BODY.replace("sub_1", "sub_2"), NOW))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("does not match");
    }

    @Test
    void rejectsAnotherSecret() {
        String header = WebhookSignatureHelper.signatureHeader("pdl_ntfset_other", TIMESTAMP, BODY);

        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, header, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class);
    }

    /** A recorded webhook cannot be replayed later, nor one with a timestamp in the future. */
    @Test
    void rejectsTimestampsOutOfTolerance() {
        String header = WebhookSignatureHelper.signatureHeader(SECRET, TIMESTAMP, BODY);

        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, header, BODY,
                NOW.plus(WebhookSignatureHelper.TOLERANCE).plusSeconds(1)))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("tolerance");
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, header, BODY,
                NOW.minus(WebhookSignatureHelper.TOLERANCE).minusSeconds(1)))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("tolerance");
        assertThatCode(() -> WebhookSignatureHelper.verify(SECRET, header, BODY,
                NOW.plus(WebhookSignatureHelper.TOLERANCE))).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrIncompleteHeaders() {
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, null, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, " ", BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "ts=1", null, NOW))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "h1=" + EXPECTED, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("incomplete");
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "ts=" + TIMESTAMP + ";junk;=x", BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("incomplete");
    }

    @Test
    void rejectsAnInvalidTimestampOrSignature() {
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "ts=soon;h1=" + EXPECTED, BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("timestamp is invalid");
        assertThatThrownBy(() -> WebhookSignatureHelper.verify(SECRET, "ts=" + TIMESTAMP + ";h1=not-hex", BODY, NOW))
                .isInstanceOf(WebhookSignatureException.class).hasMessageContaining("does not match");
    }
}

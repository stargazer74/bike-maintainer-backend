package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.exception.WebhookSignatureException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies webhooks signed after the Standard Webhooks specification (https://www.standardwebhooks.com), as Polar
 * sends them: HMAC-SHA256 over {@code <webhook-id>.<webhook-timestamp>.<body>} with the base64-decoded part of the
 * {@code whsec_…} secret; the header holds one or more space-separated {@code v1,<base64 signature>}.
 */
public final class WebhookSignatureHelper {

    /** Older or newer messages are rejected, so a recorded webhook cannot be replayed later. */
    static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final String SECRET_PREFIX = "whsec_";
    private static final String SIGNATURE_VERSION = "v1,";

    private WebhookSignatureHelper() {
    }

    /** @throws WebhookSignatureException if the signature is missing, wrong or the timestamp is out of tolerance */
    public static void verify(String secret, String id, String timestamp, String signatureHeader, String body,
                              Instant now) {
        if (isBlank(id) || isBlank(timestamp) || isBlank(signatureHeader) || body == null) {
            throw new WebhookSignatureException("Webhook headers are missing");
        }
        Instant sent;
        try {
            sent = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
        } catch (NumberFormatException e) {
            throw new WebhookSignatureException("Webhook timestamp is invalid");
        }
        if (Duration.between(sent, now).abs().compareTo(TOLERANCE) > 0) {
            throw new WebhookSignatureException("Webhook timestamp is out of tolerance");
        }
        byte[] expected = sign(secret, id + "." + timestamp.trim() + "." + body);
        for (String candidate : signatureHeader.trim().split(" +")) {
            if (candidate.startsWith(SIGNATURE_VERSION) && matches(expected,
                    candidate.substring(SIGNATURE_VERSION.length()))) {
                return;
            }
        }
        throw new WebhookSignatureException("Webhook signature does not match");
    }

    /** Signature as the sender computes it; also used by tests to sign their own webhooks. */
    public static String signature(String secret, String id, String timestamp, String body) {
        return SIGNATURE_VERSION + Base64.getEncoder().encodeToString(sign(secret, id + "." + timestamp + "." + body));
    }

    private static byte[] sign(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(secret), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private static byte[] key(String secret) {
        String encoded = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The webhook secret is not valid base64 after " + SECRET_PREFIX, e);
        }
    }

    /** Constant-time comparison, so the time of a failed check reveals nothing about the right signature. */
    private static boolean matches(byte[] expected, String encoded) {
        try {
            return MessageDigest.isEqual(expected, Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

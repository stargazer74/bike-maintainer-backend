package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.exception.WebhookSignatureException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Verifies webhooks of Paddle (https://developer.paddle.com/webhooks/signature-verification): the header
 * {@code Paddle-Signature} holds {@code ts=<unix seconds>;h1=<hex HMAC-SHA256>}, computed with the secret key of the
 * notification destination over {@code <ts>:<raw body>}. While the secret is rotated, the header carries one
 * {@code h1} per secret; any of them may match.
 */
public final class WebhookSignatureHelper {

    /**
     * Older or newer messages are rejected, so a recorded webhook cannot be replayed later. Paddle signs every
     * delivery attempt anew, so retries are not affected; its own SDKs use 5 seconds, a little more allows for clock
     * drift.
     */
    static final Duration TOLERANCE = Duration.ofSeconds(30);
    private static final String TIMESTAMP_KEY = "ts";
    private static final String SIGNATURE_KEY = "h1";

    private WebhookSignatureHelper() {
    }

    /** @throws WebhookSignatureException if the signature is missing, wrong or the timestamp is out of tolerance */
    public static void verify(String secret, String signatureHeader, String body, Instant now) {
        if (isBlank(signatureHeader) || body == null) {
            throw new WebhookSignatureException("Webhook signature is missing");
        }
        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = part.substring(0, separator).trim();
            String value = part.substring(separator + 1).trim();
            if (TIMESTAMP_KEY.equals(key)) {
                timestamp = value;
            } else if (SIGNATURE_KEY.equals(key)) {
                signatures.add(value);
            }
        }
        if (isBlank(timestamp) || signatures.isEmpty()) {
            throw new WebhookSignatureException("Webhook signature is incomplete");
        }
        Instant sent;
        try {
            sent = Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (NumberFormatException e) {
            throw new WebhookSignatureException("Webhook timestamp is invalid");
        }
        if (Duration.between(sent, now).abs().compareTo(TOLERANCE) > 0) {
            throw new WebhookSignatureException("Webhook timestamp is out of tolerance");
        }
        byte[] expected = sign(secret, timestamp + ":" + body);
        boolean matched = false;
        for (String candidate : signatures) {
            // No early return: every candidate is compared, so the time reveals nothing about which one matched.
            matched |= matches(expected, candidate);
        }
        if (!matched) {
            throw new WebhookSignatureException("Webhook signature does not match");
        }
    }

    /** The header as Paddle sends it; also used by tests to sign their own webhooks. */
    public static String signatureHeader(String secret, long timestamp, String body) {
        return TIMESTAMP_KEY + "=" + timestamp + ";" + SIGNATURE_KEY + "="
                + HexFormat.of().formatHex(sign(secret, timestamp + ":" + body));
    }

    private static byte[] sign(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** Constant-time comparison, so the time of a failed check reveals nothing about the right signature. */
    private static boolean matches(byte[] expected, String hex) {
        try {
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(hex));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

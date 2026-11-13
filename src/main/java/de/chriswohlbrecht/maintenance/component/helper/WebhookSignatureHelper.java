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
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Verifies webhooks signed after the Standard Webhooks specification (https://www.standardwebhooks.com), as Polar
 * sends them: HMAC-SHA256 over {@code <webhook-id>.<webhook-timestamp>.<body>}; the header holds one or more
 * space-separated {@code v1,<base64 signature>}. Polar has two kinds of {@code whsec_…} secrets: for endpoints created
 * before 8 September 2026 the key is the UTF-8 bytes of the full secret, for newer ones the base64-decoded part after
 * the prefix. Both are tried, so the secret can be set as Polar shows it.
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
        String content = id + "." + timestamp.trim() + "." + body;
        List<byte[]> expected = keys(secret).stream().map(key -> sign(key, content)).toList();
        for (String candidate : signatureHeader.trim().split(" +")) {
            if (candidate.startsWith(SIGNATURE_VERSION) && matchesAny(expected,
                    candidate.substring(SIGNATURE_VERSION.length()))) {
                return;
            }
        }
        throw new WebhookSignatureException("Webhook signature does not match");
    }

    /**
     * Signature as the sender computes it with a secret of the Standard Webhooks kind (base64 after the prefix); also
     * used by tests to sign their own webhooks.
     */
    public static String signature(String secret, String id, String timestamp, String body) {
        return SIGNATURE_VERSION + Base64.getEncoder().encodeToString(
                sign(base64Key(secret).orElseThrow(), id + "." + timestamp + "." + body));
    }

    /** Signature as Polar computes it for secrets created before 8 September 2026 (UTF-8 bytes of the secret). */
    public static String legacySignature(String secret, String id, String timestamp, String body) {
        return SIGNATURE_VERSION + Base64.getEncoder().encodeToString(
                sign(secret.getBytes(StandardCharsets.UTF_8), id + "." + timestamp + "." + body));
    }

    private static byte[] sign(byte[] key, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** The UTF-8 bytes of the whole secret, and the base64-decoded part after the prefix if it is valid base64. */
    private static List<byte[]> keys(String secret) {
        List<byte[]> keys = new ArrayList<>();
        keys.add(secret.getBytes(StandardCharsets.UTF_8));
        base64Key(secret).ifPresent(keys::add);
        return keys;
    }

    private static Optional<byte[]> base64Key(String secret) {
        String encoded = secret.startsWith(SECRET_PREFIX) ? secret.substring(SECRET_PREFIX.length()) : secret;
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            return key.length == 0 ? Optional.empty() : Optional.of(key);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean matchesAny(List<byte[]> expected, String encoded) {
        boolean matched = false;
        for (byte[] signature : expected) {
            // No early return: both comparisons always run, so the time reveals nothing about which key matched.
            matched |= matches(signature, encoded);
        }
        return matched;
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

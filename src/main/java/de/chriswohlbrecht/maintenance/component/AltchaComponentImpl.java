package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AltchaProperties;
import de.chriswohlbrecht.maintenance.configuration.OperatingModeCondition;
import de.chriswohlbrecht.maintenance.exception.CaptchaInvalidException;
import org.altcha.altcha.v2.Altcha;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used solutions are remembered in memory until their challenge expires; that suffices for a single instance and
 * a restart only re-allows solutions that expire within minutes anyway.
 */
@Component
@Conditional(OperatingModeCondition.Portal.class)
public class AltchaComponentImpl implements IAltchaComponent {

    private static final String ALGORITHM = "PBKDF2/SHA-256";

    private final AltchaProperties properties;
    private final Clock clock;
    /** Signature of each used challenge → when it expires. */
    private final Map<String, Instant> usedChallenges = new ConcurrentHashMap<>();

    public AltchaComponentImpl(AltchaProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String createChallengeJson() {
        try {
            return Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                            .algorithm(ALGORITHM)
                            .cost(properties.cost())
                            .deriveKey(Altcha.pbkdf2())
                            .expiresInSeconds(properties.validity().toSeconds())
                            .hmacSignatureSecret(properties.hmacKey()))
                    .toJson();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create ALTCHA challenge", e);
        }
    }

    @Override
    public void verify(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new CaptchaInvalidException("ALTCHA solution missing");
        }
        Altcha.Challenge challenge;
        Altcha.VerifySolutionResult result;
        try {
            challenge = Altcha.parsePayload(payload).challenge();
            result = Altcha.verifySolution(payload, properties.hmacKey(), Altcha.pbkdf2());
        } catch (Exception e) {
            // The library throws on anything malformed (no Base64, no JSON, missing fields) – a client error.
            throw new CaptchaInvalidException("ALTCHA solution malformed");
        }
        if (!result.verified()) {
            throw new CaptchaInvalidException(result.expired() ? "ALTCHA challenge expired" : "ALTCHA solution invalid");
        }

        Instant now = Instant.now(clock);
        usedChallenges.values().removeIf(expiresAt -> expiresAt.isBefore(now));
        Instant expiresAt = challenge.parameters().expiresAt() == null
                ? now.plus(properties.validity())
                : Instant.ofEpochSecond(challenge.parameters().expiresAt().longValue());
        if (usedChallenges.putIfAbsent(challenge.signature(), expiresAt) != null) {
            throw new CaptchaInvalidException("ALTCHA solution already used");
        }
    }
}

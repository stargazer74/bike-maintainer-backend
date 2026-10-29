package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.configuration.AltchaProperties;
import de.chriswohlbrecht.maintenance.exception.CaptchaInvalidException;
import de.chriswohlbrecht.maintenance.support.AltchaTestSupport;
import org.altcha.altcha.v2.Altcha;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AltchaComponentImplTest {

    private static final String KEY = "test-key";

    private final AltchaComponentImpl component =
            new AltchaComponentImpl(new AltchaProperties(KEY, 1, Duration.ofMinutes(5)), Clock.systemUTC());

    @Test
    void challengeJsonHasParametersAndSignature() {
        JSONObject challenge = new JSONObject(component.createChallengeJson());

        assertThat(challenge.has("signature")).isTrue();
        assertThat(challenge.getJSONObject("parameters").getString("algorithm")).isEqualTo("PBKDF2/SHA-256");
        assertThat(challenge.getJSONObject("parameters").getLong("expiresAt"))
                .isBetween(Instant.now().plusSeconds(290).getEpochSecond(),
                        Instant.now().plusSeconds(310).getEpochSecond());
    }

    @Test
    void validSolutionPasses() {
        String payload = AltchaTestSupport.solve(component.createChallengeJson());

        assertThatCode(() -> component.verify(payload)).doesNotThrowAnyException();
    }

    @Test
    void sameSolutionTwiceFails() {
        String payload = AltchaTestSupport.solve(component.createChallengeJson());
        component.verify(payload);

        assertThatThrownBy(() -> component.verify(payload)).isInstanceOf(CaptchaInvalidException.class);
    }

    @Test
    void expiredChallengeFails() throws Exception {
        String expired = Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                .algorithm("PBKDF2/SHA-256").cost(1).deriveKey(Altcha.pbkdf2())
                .expiresAt(Instant.now().minusSeconds(60).getEpochSecond())
                .hmacSignatureSecret(KEY)).toJson();
        String payload = AltchaTestSupport.solve(expired);

        assertThatThrownBy(() -> component.verify(payload)).isInstanceOf(CaptchaInvalidException.class);
    }

    @Test
    void foreignSignatureFails() {
        AltchaComponentImpl foreign =
                new AltchaComponentImpl(new AltchaProperties("other-key", 1, Duration.ofMinutes(5)), Clock.systemUTC());
        String payload = AltchaTestSupport.solve(foreign.createChallengeJson());

        assertThatThrownBy(() -> component.verify(payload)).isInstanceOf(CaptchaInvalidException.class);
    }

    @Test
    void garbagePayloadFails() {
        String emptyObject = Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> component.verify("not-base64!")).isInstanceOf(CaptchaInvalidException.class);
        assertThatThrownBy(() -> component.verify(emptyObject)).isInstanceOf(CaptchaInvalidException.class);
        assertThatThrownBy(() -> component.verify("")).isInstanceOf(CaptchaInvalidException.class);
        assertThatThrownBy(() -> component.verify(null)).isInstanceOf(CaptchaInvalidException.class);
    }
}

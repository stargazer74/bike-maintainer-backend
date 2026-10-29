package de.chriswohlbrecht.maintenance.support;

import org.altcha.altcha.v2.Altcha;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Solves ALTCHA challenges like the browser widget does and builds the payload the API expects. */
public final class AltchaTestSupport {

    private AltchaTestSupport() {
    }

    /** @param challengeJson challenge as returned by {@code GET /api/v1/auth/challenge} */
    public static String solve(String challengeJson) {
        try {
            JSONObject challenge = new JSONObject(challengeJson);
            // parsePayload is the only public way to turn the JSON into a Challenge; the solution is a placeholder.
            Altcha.Challenge parsed = Altcha.parsePayload(encode(challenge, new JSONObject()
                    .put("counter", 0).put("derivedKey", ""))).challenge();
            Altcha.Solution solution = Altcha.solveChallenge(parsed, Altcha.pbkdf2());
            return encode(challenge, new JSONObject()
                    .put("counter", solution.counter())
                    .put("derivedKey", solution.derivedKey()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not solve ALTCHA challenge", e);
        }
    }

    private static String encode(JSONObject challenge, JSONObject solution) {
        String json = new JSONObject().put("challenge", challenge).put("solution", solution).toString();
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}

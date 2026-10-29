package de.chriswohlbrecht.maintenance.component;

/** ALTCHA proof-of-work captcha (protocol v2) for public endpoints that create accounts or send emails. */
public interface IAltchaComponent {

    /**
     * A new signed challenge as produced by the ALTCHA library. Passed to the client unchanged: re-serialising it
     * into an own model could change the signed content.
     */
    String createChallengeJson();

    /**
     * Accepts each solved challenge only once.
     *
     * @param payload Base64 payload of the ALTCHA widget
     * @throws de.chriswohlbrecht.maintenance.exception.CaptchaInvalidException missing, invalid, expired or used
     */
    void verify(String payload);
}

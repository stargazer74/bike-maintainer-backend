package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.EmailRequest;
import de.chriswohlbrecht.maintenance.api.model.RegisterRequest;
import de.chriswohlbrecht.maintenance.api.model.TokenRequest;

/**
 * Self-registration in portal mode. All methods answer the same for registered and unknown addresses, so the API
 * does not reveal which addresses have an account.
 */
public interface IRegistrationComponent {

    /** A new ALTCHA challenge (JSON of the library), see {@link IAltchaComponent#createChallengeJson()}. */
    String createChallengeJson();

    /**
     * New address: creates an unverified account and sends the verification link. Existing address: changes nothing
     * and sends the owner a notice instead. Filled honeypot: does nothing.
     *
     * @param acceptLanguage raw {@code Accept-Language} header, used when the request has no language
     * @throws de.chriswohlbrecht.maintenance.exception.CaptchaInvalidException    ALTCHA solution not valid
     * @throws de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException daily email limit reached
     */
    void register(RegisterRequest request, String acceptLanguage);

    /** @throws de.chriswohlbrecht.maintenance.exception.InvalidTokenException unknown, expired or used token */
    void verifyEmail(TokenRequest request);

    /**
     * Sends a new verification link (older links become invalid), but only for an existing unverified account.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.CaptchaInvalidException    ALTCHA solution not valid
     * @throws de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException daily email limit reached
     */
    void resendVerification(EmailRequest request);
}

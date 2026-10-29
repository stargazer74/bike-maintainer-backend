package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ChangePasswordRequest;
import de.chriswohlbrecht.maintenance.api.model.EmailRequest;
import de.chriswohlbrecht.maintenance.api.model.PasswordResetConfirmRequest;

/** Changing the password and resetting a forgotten one by email (portal mode only). */
public interface IPasswordComponent {

    /**
     * Sends a reset link valid for {@link PasswordComponentImpl#RESET_VALIDITY} (older links become invalid), but
     * only for an existing active account; otherwise does nothing, so the API does not reveal registered addresses.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.CaptchaInvalidException    ALTCHA solution not valid
     * @throws de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException daily email limit reached
     */
    void requestReset(EmailRequest request);

    /**
     * Sets the new password, marks the address as verified (the link proves ownership), lifts the account lock and
     * ends all sessions of the user.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidPasswordException new password longer than 72 bytes;
     *                                                                           the token stays valid
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidTokenException    unknown, expired or used token, or
     *                                                                           the account is deactivated
     */
    void confirmReset(PasswordResetConfirmRequest request);

    /**
     * Changes the password of the logged-in user, invalidates open reset links and ends all other sessions.
     *
     * @param currentSessionId the session of this request, which stays valid
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidPasswordException current password wrong, or new
     *                                                                           password longer than 72 bytes
     */
    void changePassword(ChangePasswordRequest request, String currentSessionId);
}

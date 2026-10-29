package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

/**
 * Emails about the account, in the language of the user. Each email counts towards the daily limit; when it is
 * exhausted, the email is dropped (logged) – callers check {@link IMailQuotaComponent#ensureAvailable()} first.
 */
public interface IAccountMailComponent {

    /** Link to confirm the email address: {@code {BM_PUBLIC_URL}/email-bestaetigen?token=…}. */
    void sendVerification(AppUser user, String token);

    /** Someone tried to register with the address of this existing account; links to login and password reset. */
    void sendRegistrationExists(AppUser user);
}

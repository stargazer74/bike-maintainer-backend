package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.CancellationNotice;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

import java.time.LocalDate;

/**
 * Emails about the account, in the language of the user. Each email except the deletion notice and the cancellation
 * notice counts towards the daily limit; when it is exhausted, the email is dropped (logged) – callers check
 * {@link IMailQuotaComponent#ensureAvailable()} first.
 */
public interface IAccountMailComponent {

    /** Link to confirm the email address: {@code {BM_PUBLIC_URL}/email-bestaetigen?token=…}. */
    void sendVerification(AppUser user, String token);

    /** Link to set a new password: {@code {BM_PUBLIC_URL}/passwort-zuruecksetzen?token=…}. */
    void sendPasswordReset(AppUser user, String token);

    /** Someone tried to register with the address of this existing account; links to login and password reset. */
    void sendRegistrationExists(AppUser user);

    /**
     * The expired account will be deleted on {@code deletionDay} unless the person logs in before; links to the login.
     * Not counted towards the daily limit – the caller limits these notices itself
     * ({@code bm.retention.notice-daily-limit}).
     */
    void sendDeletionNotice(AppUser user, LocalDate deletionDay);

    /**
     * Tells an administrator about a subscription cancelled after its first year, to end it and refund the rest by
     * hand. Not counted towards the daily limit: it is rare and must not get lost.
     */
    void sendCancellationNotice(AppUser admin, CancellationNotice notice);
}

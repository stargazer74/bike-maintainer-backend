package de.chriswohlbrecht.maintenance.component;

/** Daily limit for all emails together ({@code bm.mail.daily-limit}), counted in the database. */
public interface IMailQuotaComponent {

    /**
     * Checks before handling a request that may send an email, so that the answer does not depend on whether an
     * email would actually be sent.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.MailQuotaExceededException limit reached today
     */
    void ensureAvailable();

    /** Counts one email; {@code false} (and nothing counted) if that would exceed today's limit. */
    boolean tryReserve();
}

package de.chriswohlbrecht.maintenance.component;

/** Daily housekeeping of registration data ({@code bm.cleanup.cron}). */
public interface IAccountCleanupComponent {

    /** Deletes used/expired tokens and accounts whose email was not verified within 7 days. */
    void cleanUp();
}

package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

/**
 * Deletes expired accounts (portal mode, backend#140): {@code bm.retention.period} after the access (trial or paid
 * period) ended, and never without a notice by email {@code bm.retention.notice-period} before. Login times are not
 * recorded; only a login after the notice keeps the account.
 */
public interface IAccountRetentionComponent {

    /** Daily run ({@code bm.cleanup.cron}): sends due notices, deletes accounts whose notice period is over. */
    void applyRetention();

    /**
     * Called on every successful login (browser and app, including the app's session renewal). After a deletion
     * notice it keeps the account for another {@code bm.retention.period}; otherwise it changes nothing.
     */
    void keepAfterLogin(AppUser user);
}

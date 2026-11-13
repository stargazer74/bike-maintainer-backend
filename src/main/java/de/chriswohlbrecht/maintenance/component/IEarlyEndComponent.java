package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.Subscription;

/**
 * Carries out the early end of subscriptions cancelled after their first year (portal mode, backend#147), planned by
 * {@link ICancellationNoticeComponent}: on the day after the end of the one-month notice the subscription is revoked
 * at the provider and the rest of its last payment is refunded pro rata. Steps are saved one by one, so a run that
 * breaks off continues where it stopped and never refunds twice; after a few failed runs, or when something does
 * not fit, the administrators are asked to finish it by hand.
 */
public interface IEarlyEndComponent {

    /** Daily run ({@code bm.cleanup.cron}). */
    void carryOutDueEarlyEnds();

    /**
     * Called for each subscription of an account in the transaction that deletes it: an early end still under way
     * (planned, revoked or refunding) is handed over to the administrators once the deletion is committed, so the
     * promised refund is not lost with the row.
     */
    void accountDeleted(Subscription subscription);
}

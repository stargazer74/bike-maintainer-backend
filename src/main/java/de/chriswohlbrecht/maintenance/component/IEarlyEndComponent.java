package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
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

    /**
     * Called after a subscription was revoked because its account is deleted (outside of a transaction): after the
     * first year the prepaid rest from today on is refunded pro rata. The administrators get an email with the result,
     * also when it fails and has to be finished by hand. Does nothing in the first year and for a subscription with an
     * early end of its own (that one is handed over by {@link #accountDeleted}).
     *
     * @param before state at the provider read before the revoke (it drops the paid period afterwards)
     */
    void refundAfterDeletion(Subscription subscription, ProviderSubscription before);
}

package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.Subscription;

/**
 * Cancellations after the first year (portal mode, backend#145/#147): the provider would let the subscription run
 * until the end of the paid year, but after the first year it may only run one more month, and the rest is refunded.
 * With {@code bm.subscription.early-end.enabled} the early end is planned on the subscription and carried out by
 * {@link IEarlyEndComponent}; without it, the administrators get an email to do it by hand.
 */
public interface ICancellationNoticeComponent {

    /**
     * Called once when the provider reports a cancellation that was not known before, before the subscription is
     * saved. Does nothing during the first year (the subscription ends with its term), when the paid period ends
     * within the month anyway, and when an early end is already under way.
     */
    void cancellationReceived(AppUser owner, Subscription subscription, ProviderSubscription current);

    /**
     * Called when a cancelled subscription runs on again (the customer took the cancellation back), before it is
     * saved: a planned early end that has not started is dropped.
     */
    void cancellationWithdrawn(Subscription subscription);
}

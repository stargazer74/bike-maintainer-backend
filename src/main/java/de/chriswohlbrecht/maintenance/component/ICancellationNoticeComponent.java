package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

/**
 * Cancellations after the first year (portal mode, backend#145, stage 1): the provider would let the subscription run
 * until the end of the paid year, but after the first year it may only run one more month, and the rest is refunded.
 * Until that is automated, the administrators get an email with everything needed to do it by hand.
 */
public interface ICancellationNoticeComponent {

    /**
     * Called once when the provider reports a cancellation that was not known before. Does nothing during the first
     * year (the subscription ends with its term) and when the paid period ends within the month anyway.
     */
    void cancellationReceived(AppUser owner, ProviderSubscription subscription);
}

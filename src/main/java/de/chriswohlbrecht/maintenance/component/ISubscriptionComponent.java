package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

import java.util.Optional;

/** Subscription with the payment provider (portal mode only). */
public interface ISubscriptionComponent {

    /**
     * Starts the checkout for the logged-in user.
     *
     * @return address of the payment page of the web app with the provider's checkout attached
     * @throws de.chriswohlbrecht.maintenance.exception.AlreadySubscribedException         a subscription is active
     * @throws de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException provider not reachable
     */
    String startCheckout();

    /** @return address of the provider's customer portal; empty if the user never had a subscription */
    Optional<String> openCustomerPortal();

    /**
     * Verifies a webhook of the provider and brings the affected subscription up to date.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.WebhookSignatureException         signature wrong or too old
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidWebhookException           body not readable
     * @throws de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException provider not reachable;
     *                                                                                     the provider retries
     */
    void handleWebhook(String signature, String body);

    /**
     * Daily run ({@code bm.cleanup.cron}): asks the provider again for every subscription it reported running whose
     * paid period is over, in case a webhook got lost.
     */
    void resyncOverdue();

    /**
     * Ends all running subscriptions of the user at the provider, before the account is deleted. Must not run inside
     * a transaction (calls to the provider).
     *
     * @throws de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException provider not reachable
     */
    void revokeSubscriptionsOf(AppUser user);

    /**
     * Removes the subscriptions of the user, in the transaction that deletes the account. An early end still under
     * way is handed over to the administrators, so its refund is not lost.
     */
    void removeSubscriptionsOf(AppUser user);
}

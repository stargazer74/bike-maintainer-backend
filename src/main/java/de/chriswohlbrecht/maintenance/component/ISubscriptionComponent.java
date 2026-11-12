package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;

import java.util.Optional;

/** Subscription with the payment provider (portal mode only). */
public interface ISubscriptionComponent {

    /**
     * Starts the checkout for the logged-in user.
     *
     * @return address of the provider's hosted checkout page
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
    void handleWebhook(String id, String timestamp, String signature, String body);

    /** Ends all running subscriptions of the user at the provider and removes them, before the account is deleted. */
    void endSubscriptionsOf(AppUser user);
}

package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;

import java.util.Optional;

/**
 * The payment provider (Polar). Every method throws
 * {@link de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException} if the provider cannot be
 * reached or answers unexpectedly.
 */
public interface IPaymentProviderService {

    /**
     * Starts a checkout of the subscription product for the account with the given reference.
     *
     * @return address of the provider's hosted checkout page
     */
    String createCheckout(String customerRef, String email, String successUrl, String returnUrl);

    /** @return short-lived address of the customer portal, empty if the provider does not know the customer */
    Optional<String> createCustomerPortal(String customerRef, String returnUrl);

    /** @return the subscription, empty if the provider does not know it */
    Optional<ProviderSubscription> getSubscription(String subscriptionId);

    /** Ends the subscription immediately (account deletion); an unknown subscription counts as ended. */
    void revokeSubscription(String subscriptionId);
}

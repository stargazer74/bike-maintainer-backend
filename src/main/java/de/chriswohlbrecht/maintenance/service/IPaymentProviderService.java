package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.component.model.ProviderOrder;
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

    /**
     * Ends the subscription immediately (account deletion, early end after the first year).
     *
     * @return true if it was ended now; false if the provider does not know it or it had ended already
     */
    boolean revokeSubscription(String subscriptionId);

    /**
     * @return the newest order paying a period of the subscription (also if refunded already), empty if there is
     *         none or the newest one is not paid
     */
    Optional<ProviderOrder> findLastPeriodOrder(String subscriptionId);

    /** @return the order, empty if the provider does not know it */
    Optional<ProviderOrder> getOrder(String orderId);

    /**
     * @return sum of the refunds of the order that carry the marker and went through or are still pending, in cents;
     *         0 if there are none
     */
    long markedRefundAmount(String orderId, String marker);

    /**
     * Refunds part of an order because the customer cancelled; the provider adds the matching tax.
     *
     * @param netAmount  amount before taxes, in cents, at least 1
     * @param marker     stored with the refund at the provider, to recognise it there (the subscription id)
     */
    void refund(String orderId, long netAmount, String marker);
}

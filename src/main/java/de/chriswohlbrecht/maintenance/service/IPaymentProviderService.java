package de.chriswohlbrecht.maintenance.service;

import de.chriswohlbrecht.maintenance.component.model.ProviderOrder;
import de.chriswohlbrecht.maintenance.component.model.ProviderSubscription;

import java.util.Optional;

/**
 * The payment provider (Paddle). Every method throws
 * {@link de.chriswohlbrecht.maintenance.exception.PaymentProviderUnavailableException} if the provider cannot be
 * reached or answers unexpectedly.
 */
public interface IPaymentProviderService {

    /**
     * Starts a checkout of the subscription for the account with the given reference.
     *
     * @return address of the payment page with the checkout attached (the provider's default payment link)
     */
    String createCheckout(String customerRef, String email);

    /**
     * @return short-lived address of the customer portal of the subscription's customer, empty if the provider does
     *         not know the subscription
     */
    Optional<String> createCustomerPortal(String subscriptionId);

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

package de.chriswohlbrecht.maintenance.component.model;

import java.time.OffsetDateTime;

/**
 * Current state of a subscription as the payment provider reports it.
 *
 * @param id                 id of the subscription at the provider
 * @param status             provider status, e.g. {@code active}, {@code past_due}, {@code canceled}
 * @param currentPeriodEnd   end of the paid period; null if unknown
 * @param endedAt            when the subscription ended for good; null while it runs
 * @param cancelAtPeriodEnd  cancelled by the customer, runs until the end of the period
 * @param customerRef        our reference of the account (custom data of the subscription)
 * @param startedAt          when the subscription started; null if unknown
 * @param currentPeriodStart start of the paid period; null if unknown
 * @param canceledAt         when the customer cancelled; null if not cancelled or unknown
 * @param amount             price per period in the smallest unit of the currency (cents), as the price is set up
 *                           (with Paddle usually including tax); null if unknown
 * @param currency           ISO 4217 code of the price, e.g. {@code eur}; null if unknown
 */
public record ProviderSubscription(String id, String status, OffsetDateTime currentPeriodEnd, OffsetDateTime endedAt,
                                   boolean cancelAtPeriodEnd, String customerRef, OffsetDateTime startedAt,
                                   OffsetDateTime currentPeriodStart, OffsetDateTime canceledAt, Long amount,
                                   String currency) {

    /** Without the details that only the cancellation after the first year needs. */
    public ProviderSubscription(String id, String status, OffsetDateTime currentPeriodEnd, OffsetDateTime endedAt,
                                boolean cancelAtPeriodEnd, String customerRef) {
        this(id, status, currentPeriodEnd, endedAt, cancelAtPeriodEnd, customerRef, null, null, null, null, null);
    }
}

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
 * @param customerRef        our reference of the account ({@code external_id} of the customer)
 */
public record ProviderSubscription(String id, String status, OffsetDateTime currentPeriodEnd, OffsetDateTime endedAt,
                                   boolean cancelAtPeriodEnd, String customerRef) {
}

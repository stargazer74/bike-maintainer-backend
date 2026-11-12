package de.chriswohlbrecht.maintenance.component.model;

import java.time.LocalDate;

/**
 * A subscription cancelled after its first year (§ 309 Nr. 9 BGB): it has to end one month after the cancellation was
 * received, and the prepaid rest is refunded pro rata. Handled by hand in the provider's dashboard for now
 * (backend#145).
 *
 * @param userId         internal id of the account
 * @param subscriptionId id of the subscription at the provider
 * @param receivedOn     day the cancellation was received
 * @param endsOn         last day of access after the one-month notice
 * @param paidUntil      last day of the paid period
 * @param refund         pro-rata refund for the days after {@code endsOn}, in the smallest unit of the currency;
 *                       null if the provider did not report the price
 * @param currency       ISO 4217 code of the price; null if unknown
 */
public record CancellationNotice(Long userId, String subscriptionId, LocalDate receivedOn, LocalDate endsOn,
                                 LocalDate paidUntil, Long refund, String currency) {
}

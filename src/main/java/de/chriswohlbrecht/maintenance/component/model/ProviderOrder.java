package de.chriswohlbrecht.maintenance.component.model;

/**
 * A payment at the provider (Paddle transaction). Amounts in the smallest unit of the currency (cents), net = before taxes;
 * the provider refunds the matching tax itself.
 *
 * @param id               id of the order at the provider
 * @param netAmount        paid amount after discounts, before taxes
 * @param refundedAmount   net amount refunded so far
 * @param refundableAmount net amount that can still be refunded
 * @param currency         ISO 4217 code, e.g. {@code eur}
 */
public record ProviderOrder(String id, long netAmount, long refundedAmount, long refundableAmount, String currency) {
}

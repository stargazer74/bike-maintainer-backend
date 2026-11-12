package de.chriswohlbrecht.maintenance.persistence.type;

/** State of a subscription as reported by the payment provider. */
public enum SubscriptionStatus {
    /** Paid and renewing. */
    ACTIVE,
    /** A renewal payment failed; the provider still retries, so access continues. */
    PAST_DUE,
    /** Cancelled by the customer; access continues until the end of the paid period. */
    CANCELED,
    /** Ended by the provider (period over after cancellation, or retries given up). */
    ENDED;

    /** Whether the provider still treats the subscription as running, regardless of the paid period. */
    public boolean isRunning() {
        return this == ACTIVE || this == PAST_DUE;
    }
}

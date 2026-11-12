package de.chriswohlbrecht.maintenance.persistence.type;

/** Steps of the early end of a subscription cancelled after its first year (backend#147). */
public enum EarlyEndState {
    /** Waiting for the end day; withdrawn if the customer takes the cancellation back. */
    SCHEDULED,
    /** Ended at the provider; the refund is still to do. */
    REVOKED,
    /** Refund requested; the order and the amount refunded before are stored, so a retry never refunds twice. */
    REFUNDING,
    /** Ended and refunded. */
    REFUNDED,
    /** Given up or not possible automatically; the administrators were asked to finish it by hand. */
    MANUAL;

    /** Whether the nightly run still has to do something. */
    public boolean isOpen() {
        return this == SCHEDULED || this == REVOKED || this == REFUNDING;
    }
}

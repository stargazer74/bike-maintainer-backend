package de.chriswohlbrecht.maintenance.component.model;

/**
 * Which email the administrators get about a subscription cancelled after its first year; each has its own message
 * key {@code mail.<key>.subject/.body}.
 */
public enum CancellationMail {
    /** Automatic early end off (or not possible): end and refund by hand. */
    MANUAL("cancellationNotice"),
    /** Will be ended and refunded automatically on the day after {@code endsOn}. */
    SCHEDULED("earlyEndScheduled"),
    /** Ended and refunded; {@code refund} is the amount actually refunded (net). */
    DONE("earlyEndDone"),
    /** The automatic early end stopped; the reason says how far it got. */
    FAILED("earlyEndFailed");

    private final String key;

    CancellationMail(String key) {
        this.key = key;
    }

    public String messageKey() {
        return "mail." + key;
    }
}

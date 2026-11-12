package de.chriswohlbrecht.maintenance.exception;

/** The account is read-only: neither the trial nor a paid subscription is active (403, portal mode only). */
public class SubscriptionRequiredException extends RuntimeException {

    public static final String CODE = "SUBSCRIPTION_REQUIRED";

    public SubscriptionRequiredException() {
        super("The account is read-only: neither the trial nor a subscription is active");
    }
}

package de.chriswohlbrecht.maintenance.exception;

/** Checkout while a paid subscription is already active (409). */
public class AlreadySubscribedException extends RuntimeException {

    public static final String CODE = "ALREADY_SUBSCRIBED";

    public AlreadySubscribedException() {
        super("A subscription is already active");
    }
}

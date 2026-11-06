package de.chriswohlbrecht.maintenance.exception;

/** The action would leave the portal without an active admin, e.g. the last admin deleting their account (409). */
public class LastAdminException extends RuntimeException {

    public static final String CODE = "LAST_ADMIN";

    public LastAdminException(String message) {
        super(message);
    }
}

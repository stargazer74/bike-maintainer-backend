package de.chriswohlbrecht.maintenance.exception;

/**
 * A token from an email link is unknown, expired or already used, or a device token chosen by the app is not new
 * (400).
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}

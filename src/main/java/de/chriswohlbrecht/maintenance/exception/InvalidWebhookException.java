package de.chriswohlbrecht.maintenance.exception;

/** A correctly signed webhook whose body cannot be read (400). */
public class InvalidWebhookException extends RuntimeException {

    public InvalidWebhookException(String message) {
        super(message);
    }
}

package de.chriswohlbrecht.maintenance.exception;

/** A webhook whose signature is missing, wrong or too old (403); nothing is processed. */
public class WebhookSignatureException extends RuntimeException {

    public WebhookSignatureException(String message) {
        super(message);
    }
}

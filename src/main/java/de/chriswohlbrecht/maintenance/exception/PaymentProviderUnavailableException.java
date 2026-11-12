package de.chriswohlbrecht.maintenance.exception;

/** The payment provider could not be reached or answered unexpectedly (503); the client may retry later. */
public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message) {
        super(message);
    }

    public PaymentProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

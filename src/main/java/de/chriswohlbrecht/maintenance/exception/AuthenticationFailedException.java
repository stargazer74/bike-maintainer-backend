package de.chriswohlbrecht.maintenance.exception;

/** Wrong credentials, or no valid login for the request (401). */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}

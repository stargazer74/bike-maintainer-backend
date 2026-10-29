package de.chriswohlbrecht.maintenance.exception;

/** A new password the bean validation cannot catch, e.g. longer than BCrypt's 72 bytes (400). */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}

package de.chriswohlbrecht.maintenance.exception;

/** The password confirming a sensitive action of the logged-in user (deleting the account) is wrong (403). */
public class WrongPasswordException extends RuntimeException {

    public static final String CODE = "WRONG_PASSWORD";

    public WrongPasswordException(String message) {
        super(message);
    }
}

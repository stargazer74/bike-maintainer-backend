package de.chriswohlbrecht.maintenance.exception;

/** The ALTCHA solution is missing, invalid, expired or already used (400 {@code CAPTCHA_INVALID}). */
public class CaptchaInvalidException extends RuntimeException {

    public static final String CODE = "CAPTCHA_INVALID";

    public CaptchaInvalidException(String message) {
        super(message);
    }
}

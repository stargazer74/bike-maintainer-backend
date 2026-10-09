package de.chriswohlbrecht.maintenance.exception;

import lombok.Getter;

/**
 * Credentials are correct, but the account cannot be used (403). Only thrown after the password has been
 * verified, so it does not reveal anything to someone who does not know the password.
 */
@Getter
public class AccountUnusableException extends RuntimeException {

    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    private final String code;

    public AccountUnusableException(String code, String message) {
        super(message);
        this.code = code;
    }
}

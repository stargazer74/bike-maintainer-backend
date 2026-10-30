package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The update names no field to change; the generator does not enforce the schema's minProperties. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidUserUpdateException extends RuntimeException {

    public InvalidUserUpdateException(String message) {
        super(message);
    }
}

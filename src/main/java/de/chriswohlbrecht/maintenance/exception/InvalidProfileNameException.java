package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The profile name is blank after trimming; the schema's length check alone does not catch that. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidProfileNameException extends RuntimeException {

    public InvalidProfileNameException(String message) {
        super(message);
    }
}

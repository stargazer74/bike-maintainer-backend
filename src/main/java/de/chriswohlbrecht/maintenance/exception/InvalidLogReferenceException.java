package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidLogReferenceException extends RuntimeException {

    public InvalidLogReferenceException(String message) {
        super(message);
    }
}

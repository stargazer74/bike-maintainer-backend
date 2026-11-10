package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A worksheet template that does not fit the vehicle type, or missing or superfluous valve parameters. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidWorksheetRequestException extends RuntimeException {

    public InvalidWorksheetRequestException(String message) {
        super(message);
    }
}

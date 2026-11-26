package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A selection of template tasks that does not fit the template or the vehicle. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTemplateRequestException extends RuntimeException {

    public InvalidTemplateRequestException(String message) {
        super(message);
    }
}

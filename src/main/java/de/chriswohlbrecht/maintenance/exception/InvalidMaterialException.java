package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A material or cost entry the bean validation of the spec cannot catch: decimal places, task reference. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidMaterialException extends RuntimeException {

    public InvalidMaterialException(String message) {
        super(message);
    }
}

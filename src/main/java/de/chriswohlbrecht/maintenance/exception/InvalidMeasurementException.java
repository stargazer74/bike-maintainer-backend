package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Target or measured values that the schema allows but that do not fit together (too many decimals, min > max, ...). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidMeasurementException extends RuntimeException {

    public InvalidMeasurementException(String message) {
        super(message);
    }
}

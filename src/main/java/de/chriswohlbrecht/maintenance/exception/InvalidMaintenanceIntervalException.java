package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidMaintenanceIntervalException extends RuntimeException {

    public InvalidMaintenanceIntervalException(String message) {
        super(message);
    }
}

package de.chriswohlbrecht.maintenance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTaskReferenceException extends RuntimeException {

    public InvalidTaskReferenceException(String message) {
        super(message);
    }
}

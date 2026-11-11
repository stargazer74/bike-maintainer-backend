package de.chriswohlbrecht.maintenance.exception;

import java.io.IOException;

/** A request body is larger than allowed (413). An {@link IOException}, so it can end the reading of a body. */
public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException(long limit) {
        super("Request body larger than " + limit + " bytes");
    }
}

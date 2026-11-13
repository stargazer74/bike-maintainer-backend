package de.chriswohlbrecht.maintenance.exception;

/**
 * A mileage below the vehicle's current one was sent to {@code PUT .../mileage} (409); that endpoint only raises the
 * mileage, corrections downwards go through {@code PUT /vehicles/{vehicleId}}.
 */
public class MileageBelowCurrentException extends RuntimeException {

    public static final String CODE = "MILEAGE_BELOW_CURRENT";

    public MileageBelowCurrentException(String message) {
        super(message);
    }
}

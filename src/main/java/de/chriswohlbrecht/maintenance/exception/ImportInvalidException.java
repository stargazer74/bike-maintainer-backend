package de.chriswohlbrecht.maintenance.exception;

import java.util.List;

/**
 * An import file breaks a rule (400 {@code IMPORT_INVALID}); {@link #getDetails()} names every problem as
 * {@code <location>: <problem>}, e.g. {@code vehicle.maintenanceLogs[3]: Task with ref "t9" not found}.
 */
public class ImportInvalidException extends RuntimeException {

    public static final String CODE = "IMPORT_INVALID";

    private final List<String> details;

    public ImportInvalidException(List<String> details) {
        super("Invalid import file: " + String.join("; ", details));
        this.details = List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}

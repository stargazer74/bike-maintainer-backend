package de.chriswohlbrecht.maintenance.component.model;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Result of creating a maintenance log: {@code created} is false if a log with the same {@code clientEntryId} already
 * existed and is returned instead (200 instead of 201). The generated response model is handed on as it is: the
 * controller only serialises it.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Only serialised by the controller")
public record LogCreation(MaintenanceLogResponse log, boolean created) {

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Built once by the component, never changed")
    public LogCreation {
        // canonical constructor, only here for the annotation
    }
}

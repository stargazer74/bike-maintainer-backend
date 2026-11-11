package de.chriswohlbrecht.maintenance.component.model;

import de.chriswohlbrecht.maintenance.api.model.VehicleExport;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * An exported vehicle and the file name to offer it under. The generated export model is handed on as it is: the
 * controller only serialises it.
 */
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Only serialised by the controller")
public record VehicleExportFile(String fileName, VehicleExport content) {

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Built once by the component, never changed")
    public VehicleExportFile {
        // canonical constructor, only here for the annotation
    }
}

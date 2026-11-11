package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.VehicleExport;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.component.model.VehicleExportFile;

import java.util.Optional;

/** Moving a vehicle with all its data to another instance (Epic #82). */
public interface IVehicleExportComponent {

    /** @return empty if the vehicle does not exist or belongs to another user */
    Optional<VehicleExportFile> exportVehicle(Long vehicleId);

    /**
     * Creates a new vehicle of the current user from an export file; all or nothing.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.ImportFormatUnsupportedException unknown {@code formatVersion}
     * @throws de.chriswohlbrecht.maintenance.exception.ImportInvalidException a rule is broken, with every problem found
     */
    VehicleResponse importVehicle(VehicleExport file);
}

package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.component.model.Worksheet;

import java.util.List;
import java.util.Optional;

public interface IWorksheetComponent {

    /**
     * The worksheet templates that fit the vehicle type.
     *
     * @return empty if the vehicle does not exist or belongs to another user
     */
    Optional<List<WorksheetTemplate>> listWorksheetTemplates(Long vehicleId);

    /**
     * Empty worksheet with the vehicle header. Nothing is stored.
     *
     * @param cylinders         required for {@link WorksheetTemplate#VALVE_CLEARANCE}, not allowed otherwise
     * @param valvesPerCylinder required for {@link WorksheetTemplate#VALVE_CLEARANCE}, not allowed otherwise
     * @return empty if the vehicle does not exist or belongs to another user
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidWorksheetRequestException template not available for
     *         the vehicle type, or valve parameters missing or given for another template
     */
    Optional<Worksheet> generateWorksheet(Long vehicleId, WorksheetTemplate template, Integer cylinders,
                                          Integer valvesPerCylinder);
}

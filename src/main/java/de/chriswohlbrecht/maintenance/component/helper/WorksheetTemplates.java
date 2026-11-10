package de.chriswohlbrecht.maintenance.component.helper;

import de.chriswohlbrecht.maintenance.api.model.WorksheetTemplate;
import de.chriswohlbrecht.maintenance.persistence.type.VehicleType;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Which worksheet templates fit which vehicle type – the only place this rule lives. */
public final class WorksheetTemplates {

    private static final Map<WorksheetTemplate, Set<VehicleType>> TYPES = Map.of(
            WorksheetTemplate.VALVE_CLEARANCE,
            EnumSet.of(VehicleType.MOTORCYCLE, VehicleType.CAR, VehicleType.QUAD, VehicleType.SCOOTER),
            WorksheetTemplate.BRAKES, EnumSet.allOf(VehicleType.class),
            WorksheetTemplate.CHAIN,
            EnumSet.of(VehicleType.MOTORCYCLE, VehicleType.BICYCLE, VehicleType.E_BIKE, VehicleType.QUAD),
            WorksheetTemplate.TIRES, EnumSet.allOf(VehicleType.class));

    private WorksheetTemplates() {
    }

    /** The templates for the vehicle type, in the order of {@link WorksheetTemplate}. */
    public static List<WorksheetTemplate> availableFor(VehicleType type) {
        return Arrays.stream(WorksheetTemplate.values())
                .filter(template -> isAvailable(template, type))
                .toList();
    }

    public static boolean isAvailable(WorksheetTemplate template, VehicleType type) {
        return TYPES.get(template).contains(type);
    }
}

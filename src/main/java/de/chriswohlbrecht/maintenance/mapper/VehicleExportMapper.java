package de.chriswohlbrecht.maintenance.mapper;

import de.chriswohlbrecht.maintenance.api.model.BrakeMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.BrakeMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ChainMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.ChainMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceLog;
import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceMaterial;
import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceTask;
import de.chriswohlbrecht.maintenance.api.model.ExportedVehicle;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMaterialRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMaterialResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMeasurementsRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMeasurementsResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.api.model.TireMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.TireMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ValveClearanceMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.ValveClearanceMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.ValveMeasurementRequest;
import de.chriswohlbrecht.maintenance.api.model.ValveMeasurementResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Between the export file (Epic #82) and the normal requests and responses. References between tasks, logs and
 * materials (IDs in the API, {@code ref} in the file) are set by the caller, which knows both sides.
 */
@Mapper(componentModel = "spring")
public interface VehicleExportMapper {

    // Export: response -> file

    @Mapping(target = "specifications", ignore = true)
    @Mapping(target = "maintenanceTasks", ignore = true)
    @Mapping(target = "maintenanceLogs", ignore = true)
    ExportedVehicle toExportedVehicle(VehicleResponse vehicle);

    @Mapping(target = "ref", ignore = true)
    ExportedMaintenanceTask toExportedTask(MaintenanceTaskResponse task);

    @Mapping(target = "performedTaskRefs", ignore = true)
    @Mapping(target = "createdTaskRefs", ignore = true)
    @Mapping(target = "materials", ignore = true)
    ExportedMaintenanceLog toExportedLog(MaintenanceLogResponse log);

    @Mapping(target = "taskRef", ignore = true)
    ExportedMaintenanceMaterial toExportedMaterial(MaintenanceMaterialResponse material);

    MaintenanceMeasurementsRequest toMeasurementsRequest(MaintenanceMeasurementsResponse measurements);

    ValveClearanceMeasurementRequest toValveClearanceRequest(ValveClearanceMeasurementResponse valveClearance);

    ValveMeasurementRequest toValveRequest(ValveMeasurementResponse valve);

    BrakeMeasurementRequest toBrakeRequest(BrakeMeasurementResponse brakes);

    ChainMeasurementRequest toChainRequest(ChainMeasurementResponse chain);

    TireMeasurementRequest toTireRequest(TireMeasurementResponse tires);

    // Import: file -> request

    VehicleRequest toVehicleRequest(ExportedVehicle vehicle);

    MaintenanceTaskRequest toTaskRequest(ExportedMaintenanceTask task);

    @Mapping(target = "performedTaskIds", ignore = true)
    @Mapping(target = "newTasks", ignore = true)
    @Mapping(target = "materials", ignore = true)
    MaintenanceLogRequest toLogRequest(ExportedMaintenanceLog log);

    @Mapping(target = "taskId", ignore = true)
    MaintenanceMaterialRequest toMaterialRequest(ExportedMaintenanceMaterial material);
}

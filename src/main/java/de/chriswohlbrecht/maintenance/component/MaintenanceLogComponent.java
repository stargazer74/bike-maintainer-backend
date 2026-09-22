package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;

import java.util.List;
import java.util.Optional;

public interface MaintenanceLogComponent {

    Optional<List<MaintenanceLogResponse>> listMaintenanceLogs(Long vehicleId);

    Optional<MaintenanceLogResponse> getMaintenanceLog(Long vehicleId, Long logId);

    Optional<MaintenanceLogResponse> createMaintenanceLog(Long vehicleId, MaintenanceLogRequest request);

    Optional<MaintenanceLogResponse> updateMaintenanceLog(Long vehicleId, Long logId, MaintenanceLogRequest request);

    boolean deleteMaintenanceLog(Long vehicleId, Long logId);
}

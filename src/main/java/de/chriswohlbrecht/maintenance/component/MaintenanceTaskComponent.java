package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;

import java.util.List;
import java.util.Optional;

public interface MaintenanceTaskComponent {

    Optional<List<MaintenanceTaskResponse>> listMaintenanceTasks(Long vehicleId);

    Optional<MaintenanceTaskResponse> getMaintenanceTask(Long vehicleId, Long taskId);

    Optional<MaintenanceTaskResponse> createMaintenanceTask(Long vehicleId, MaintenanceTaskRequest request);

    Optional<MaintenanceTaskResponse> updateMaintenanceTask(Long vehicleId, Long taskId, MaintenanceTaskRequest request);

    boolean deleteMaintenanceTask(Long vehicleId, Long taskId);
}

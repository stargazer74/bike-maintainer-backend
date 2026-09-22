package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.MaintenanceTasksApi;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.component.MaintenanceTaskComponent;
import de.chriswohlbrecht.maintenance.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MaintenanceTaskController implements MaintenanceTasksApi {

    private final MaintenanceTaskComponent maintenanceTaskComponent;

    @Override
    public ResponseEntity<List<MaintenanceTaskResponse>> listMaintenanceTasks(Long vehicleId) {
        return maintenanceTaskComponent.listMaintenanceTasks(vehicleId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> vehicleNotFound(vehicleId));
    }

    @Override
    public ResponseEntity<MaintenanceTaskResponse> getMaintenanceTask(Long vehicleId, Long taskId) {
        return maintenanceTaskComponent.getMaintenanceTask(vehicleId, taskId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> taskNotFound(vehicleId, taskId));
    }

    @Override
    public ResponseEntity<MaintenanceTaskResponse> createMaintenanceTask(Long vehicleId,
                                                                           MaintenanceTaskRequest maintenanceTaskRequest) {
        return maintenanceTaskComponent.createMaintenanceTask(vehicleId, maintenanceTaskRequest)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElseThrow(() -> vehicleNotFound(vehicleId));
    }

    @Override
    public ResponseEntity<MaintenanceTaskResponse> updateMaintenanceTask(Long vehicleId, Long taskId,
                                                                           MaintenanceTaskRequest maintenanceTaskRequest) {
        return maintenanceTaskComponent.updateMaintenanceTask(vehicleId, taskId, maintenanceTaskRequest)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> taskNotFound(vehicleId, taskId));
    }

    @Override
    public ResponseEntity<Void> deleteMaintenanceTask(Long vehicleId, Long taskId) {
        if (!maintenanceTaskComponent.deleteMaintenanceTask(vehicleId, taskId)) {
            throw taskNotFound(vehicleId, taskId);
        }
        return ResponseEntity.noContent().build();
    }

    private ResourceNotFoundException vehicleNotFound(Long vehicleId) {
        return new ResourceNotFoundException("Vehicle " + vehicleId + " not found");
    }

    private ResourceNotFoundException taskNotFound(Long vehicleId, Long taskId) {
        return new ResourceNotFoundException("Maintenance task " + taskId + " not found for vehicle " + vehicleId);
    }
}

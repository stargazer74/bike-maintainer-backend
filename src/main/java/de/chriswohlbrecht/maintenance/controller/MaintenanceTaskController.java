package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.MaintenanceTasksApi;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.component.IMaintenanceTaskComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MaintenanceTaskController implements MaintenanceTasksApi {

    private final IMaintenanceTaskComponent maintenanceTaskComponent;

    @Override
    public ResponseEntity<List<MaintenanceTaskResponse>> listMaintenanceTasks(Long vehicleId) {
        return maintenanceTaskComponent.listMaintenanceTasks(vehicleId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<MaintenanceTaskResponse> getMaintenanceTask(Long vehicleId, Long taskId) {
        return maintenanceTaskComponent.getMaintenanceTask(vehicleId, taskId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<MaintenanceTaskResponse> createMaintenanceTask(Long vehicleId,
                                                                           MaintenanceTaskRequest maintenanceTaskRequest) {
        return maintenanceTaskComponent.createMaintenanceTask(vehicleId, maintenanceTaskRequest)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<MaintenanceTaskResponse> updateMaintenanceTask(Long vehicleId, Long taskId,
                                                                           MaintenanceTaskRequest maintenanceTaskRequest) {
        return maintenanceTaskComponent.updateMaintenanceTask(vehicleId, taskId, maintenanceTaskRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Void> deleteMaintenanceTask(Long vehicleId, Long taskId) {
        if (!maintenanceTaskComponent.deleteMaintenanceTask(vehicleId, taskId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}

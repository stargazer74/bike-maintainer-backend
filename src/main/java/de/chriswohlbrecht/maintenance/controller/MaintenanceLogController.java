package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.MaintenanceLogsApi;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.component.MaintenanceLogComponent;
import de.chriswohlbrecht.maintenance.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MaintenanceLogController implements MaintenanceLogsApi {

    private final MaintenanceLogComponent maintenanceLogComponent;

    @Override
    public ResponseEntity<List<MaintenanceLogResponse>> listMaintenanceLogs(Long vehicleId) {
        return maintenanceLogComponent.listMaintenanceLogs(vehicleId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> vehicleNotFound(vehicleId));
    }

    @Override
    public ResponseEntity<MaintenanceLogResponse> getMaintenanceLog(Long vehicleId, Long logId) {
        return maintenanceLogComponent.getMaintenanceLog(vehicleId, logId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> logNotFound(vehicleId, logId));
    }

    @Override
    public ResponseEntity<MaintenanceLogResponse> createMaintenanceLog(Long vehicleId,
                                                                         MaintenanceLogRequest maintenanceLogRequest) {
        return maintenanceLogComponent.createMaintenanceLog(vehicleId, maintenanceLogRequest)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElseThrow(() -> vehicleNotFound(vehicleId));
    }

    @Override
    public ResponseEntity<MaintenanceLogResponse> updateMaintenanceLog(Long vehicleId, Long logId,
                                                                         MaintenanceLogRequest maintenanceLogRequest) {
        return maintenanceLogComponent.updateMaintenanceLog(vehicleId, logId, maintenanceLogRequest)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> logNotFound(vehicleId, logId));
    }

    @Override
    public ResponseEntity<Void> deleteMaintenanceLog(Long vehicleId, Long logId) {
        if (!maintenanceLogComponent.deleteMaintenanceLog(vehicleId, logId)) {
            throw logNotFound(vehicleId, logId);
        }
        return ResponseEntity.noContent().build();
    }

    private ResourceNotFoundException vehicleNotFound(Long vehicleId) {
        return new ResourceNotFoundException("Vehicle " + vehicleId + " not found");
    }

    private ResourceNotFoundException logNotFound(Long vehicleId, Long logId) {
        return new ResourceNotFoundException("Maintenance log " + logId + " not found for vehicle " + vehicleId);
    }
}

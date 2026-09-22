package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.MaintenanceLogsApi;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.component.MaintenanceLogComponent;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceLogOutcome;
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
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<MaintenanceLogResponse> getMaintenanceLog(Long vehicleId, Long logId) {
        return maintenanceLogComponent.getMaintenanceLog(vehicleId, logId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<MaintenanceLogResponse> createMaintenanceLog(Long vehicleId,
                                                                         MaintenanceLogRequest maintenanceLogRequest) {
        MaintenanceLogOutcome outcome = maintenanceLogComponent.createMaintenanceLog(vehicleId, maintenanceLogRequest);
        return switch (outcome) {
            case MaintenanceLogOutcome.Saved saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved.response());
            case MaintenanceLogOutcome.NotFound notFound -> ResponseEntity.notFound().build();
            case MaintenanceLogOutcome.InvalidTaskReference invalid -> ResponseEntity.badRequest().build();
        };
    }

    @Override
    public ResponseEntity<MaintenanceLogResponse> updateMaintenanceLog(Long vehicleId, Long logId,
                                                                         MaintenanceLogRequest maintenanceLogRequest) {
        MaintenanceLogOutcome outcome = maintenanceLogComponent.updateMaintenanceLog(vehicleId, logId, maintenanceLogRequest);
        return switch (outcome) {
            case MaintenanceLogOutcome.Saved saved -> ResponseEntity.ok(saved.response());
            case MaintenanceLogOutcome.NotFound notFound -> ResponseEntity.notFound().build();
            case MaintenanceLogOutcome.InvalidTaskReference invalid -> ResponseEntity.badRequest().build();
        };
    }

    @Override
    public ResponseEntity<Void> deleteMaintenanceLog(Long vehicleId, Long logId) {
        if (!maintenanceLogComponent.deleteMaintenanceLog(vehicleId, logId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}

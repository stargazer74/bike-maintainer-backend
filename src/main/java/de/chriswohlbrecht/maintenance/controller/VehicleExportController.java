package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.VehicleExportApi;
import de.chriswohlbrecht.maintenance.api.model.VehicleExport;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.component.IAccessComponent;
import de.chriswohlbrecht.maintenance.component.IVehicleExportComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VehicleExportController implements VehicleExportApi {

    private final IVehicleExportComponent vehicleExportComponent;
    private final IAccessComponent accessComponent;

    @Override
    public ResponseEntity<VehicleExport> exportVehicle(Long vehicleId) {
        return vehicleExportComponent.exportVehicle(vehicleId)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename(file.fileName())
                                .build()
                                .toString())
                        .body(file.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<VehicleResponse> importVehicle(VehicleExport vehicleExport) {
        accessComponent.requireWriteAccess();
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleExportComponent.importVehicle(vehicleExport));
    }
}

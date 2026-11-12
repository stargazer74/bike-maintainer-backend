package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.VehiclesApi;
import de.chriswohlbrecht.maintenance.api.model.VehicleRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.component.IVehicleComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class VehicleController implements VehiclesApi {

    private final IVehicleComponent vehicleComponent;

    @Override
    public ResponseEntity<List<VehicleResponse>> listVehicles() {
        return ResponseEntity.ok(vehicleComponent.listVehicles());
    }

    @Override
    public ResponseEntity<VehicleResponse> getVehicle(Long vehicleId) {
        return vehicleComponent.getVehicle(vehicleId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<VehicleResponse> createVehicle(VehicleRequest vehicleRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleComponent.createVehicle(vehicleRequest));
    }

    @Override
    public ResponseEntity<VehicleResponse> updateVehicle(Long vehicleId, VehicleRequest vehicleRequest) {
        return vehicleComponent.updateVehicle(vehicleId, vehicleRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Void> deleteVehicle(Long vehicleId) {
        if (!vehicleComponent.deleteVehicle(vehicleId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}

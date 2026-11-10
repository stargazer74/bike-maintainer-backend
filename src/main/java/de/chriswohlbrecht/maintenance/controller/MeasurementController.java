package de.chriswohlbrecht.maintenance.controller;

import de.chriswohlbrecht.maintenance.api.handler.MeasurementsApi;
import de.chriswohlbrecht.maintenance.api.model.LatestMeasurementsResponse;
import de.chriswohlbrecht.maintenance.api.model.ShimCalculationRequest;
import de.chriswohlbrecht.maintenance.api.model.ShimCalculationResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleSpecifications;
import de.chriswohlbrecht.maintenance.component.IMeasurementComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeasurementController implements MeasurementsApi {

    private final IMeasurementComponent measurementComponent;

    @Override
    public ResponseEntity<VehicleSpecifications> getVehicleSpecifications(Long vehicleId) {
        return ResponseEntity.of(measurementComponent.getVehicleSpecifications(vehicleId));
    }

    @Override
    public ResponseEntity<VehicleSpecifications> updateVehicleSpecifications(Long vehicleId,
                                                                             VehicleSpecifications request) {
        return ResponseEntity.of(measurementComponent.updateVehicleSpecifications(vehicleId, request));
    }

    @Override
    public ResponseEntity<LatestMeasurementsResponse> getLatestMeasurements(Long vehicleId) {
        return ResponseEntity.of(measurementComponent.getLatestMeasurements(vehicleId));
    }

    @Override
    public ResponseEntity<ShimCalculationResponse> calculateShims(Long vehicleId, ShimCalculationRequest request) {
        return ResponseEntity.of(measurementComponent.calculateShims(vehicleId, request));
    }
}

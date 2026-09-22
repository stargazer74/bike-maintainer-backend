package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.VehicleRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;

import java.util.List;
import java.util.Optional;

public interface VehicleComponent {

    List<VehicleResponse> listVehicles();

    Optional<VehicleResponse> getVehicle(Long vehicleId);

    VehicleResponse createVehicle(VehicleRequest request);

    Optional<VehicleResponse> updateVehicle(Long vehicleId, VehicleRequest request);

    boolean deleteVehicle(Long vehicleId);
}

package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.VehicleRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;

import java.util.List;
import java.util.Optional;

public interface IVehicleComponent {

    List<VehicleResponse> listVehicles();

    Optional<VehicleResponse> getVehicle(Long vehicleId);

    VehicleResponse createVehicle(VehicleRequest request);

    Optional<VehicleResponse> updateVehicle(Long vehicleId, VehicleRequest request);

    /**
     * Raises only the current mileage; the same value changes nothing. Empty for unknown and foreign vehicles.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.MileageBelowCurrentException if below the current mileage
     */
    Optional<VehicleResponse> updateMileage(Long vehicleId, int mileage);

    boolean deleteVehicle(Long vehicleId);
}

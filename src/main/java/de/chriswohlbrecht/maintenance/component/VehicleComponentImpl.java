package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.VehicleRequest;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.mapper.VehicleMapper;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VehicleComponentImpl implements IVehicleComponent {

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;
    private final ICurrentUserComponent currentUserComponent;

    @Override
    public List<VehicleResponse> listVehicles() {
        return vehicleRepository.findAllByUser_Id(currentUserComponent.getCurrentUserId()).stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Override
    public Optional<VehicleResponse> getVehicle(Long vehicleId) {
        return findOwnVehicle(vehicleId).map(vehicleMapper::toResponse);
    }

    @Override
    public VehicleResponse createVehicle(VehicleRequest request) {
        Vehicle vehicle = vehicleMapper.toEntity(request);
        vehicle.setUser(currentUserComponent.getCurrentUser());
        return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public Optional<VehicleResponse> updateVehicle(Long vehicleId, VehicleRequest request) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            vehicleMapper.updateEntityFromRequest(request, vehicle);
            return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
        });
    }

    @Override
    public boolean deleteVehicle(Long vehicleId) {
        return findOwnVehicle(vehicleId)
                .map(vehicle -> {
                    vehicleRepository.delete(vehicle);
                    return true;
                })
                .orElse(false);
    }

    /** Vehicles of other users are treated like non-existent ones (404), so foreign ids cannot be probed. */
    private Optional<Vehicle> findOwnVehicle(Long vehicleId) {
        return vehicleRepository.findByIdAndUser_Id(vehicleId, currentUserComponent.getCurrentUserId());
    }
}

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

    @Override
    public List<VehicleResponse> listVehicles() {
        return vehicleRepository.findAll().stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Override
    public Optional<VehicleResponse> getVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId).map(vehicleMapper::toResponse);
    }

    @Override
    public VehicleResponse createVehicle(VehicleRequest request) {
        Vehicle vehicle = vehicleMapper.toEntity(request);
        return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    public Optional<VehicleResponse> updateVehicle(Long vehicleId, VehicleRequest request) {
        return vehicleRepository.findById(vehicleId).map(vehicle -> {
            vehicleMapper.updateEntityFromRequest(request, vehicle);
            return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
        });
    }

    @Override
    public boolean deleteVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> {
                    vehicleRepository.delete(vehicle);
                    return true;
                })
                .orElse(false);
    }
}

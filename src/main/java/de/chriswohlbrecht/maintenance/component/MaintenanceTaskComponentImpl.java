package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.exception.InvalidMaintenanceIntervalException;
import de.chriswohlbrecht.maintenance.mapper.MaintenanceTaskMapper;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MaintenanceTaskComponentImpl implements IMaintenanceTaskComponent {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final MaintenanceTaskMapper maintenanceTaskMapper;

    @Override
    public Optional<List<MaintenanceTaskResponse>> listMaintenanceTasks(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> maintenanceTaskRepository.findAllByVehicle_Id(vehicleId).stream()
                        .map(maintenanceTaskMapper::toResponse)
                        .toList());
    }

    @Override
    public Optional<MaintenanceTaskResponse> getMaintenanceTask(Long vehicleId, Long taskId) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId))
                .map(maintenanceTaskMapper::toResponse);
    }

    @Override
    public Optional<MaintenanceTaskResponse> createMaintenanceTask(Long vehicleId, MaintenanceTaskRequest request) {
        return vehicleRepository.findById(vehicleId).map(vehicle -> {
            validateInterval(request);
            MaintenanceTask task = maintenanceTaskMapper.toEntity(request);
            task.setVehicle(vehicle);
            return maintenanceTaskMapper.toResponse(maintenanceTaskRepository.save(task));
        });
    }

    @Override
    public Optional<MaintenanceTaskResponse> updateMaintenanceTask(Long vehicleId, Long taskId, MaintenanceTaskRequest request) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId))
                .map(task -> {
                    validateInterval(request);
                    maintenanceTaskMapper.updateEntityFromRequest(request, task);
                    return maintenanceTaskMapper.toResponse(maintenanceTaskRepository.save(task));
                });
    }

    private static void validateInterval(MaintenanceTaskRequest request) {
        boolean oneTime = Boolean.TRUE.equals(request.getOneTime());
        if (!oneTime && request.getIntervalKm() == null && request.getIntervalMonths() == null) {
            throw new InvalidMaintenanceIntervalException(
                    "At least one of intervalKm or intervalMonths must be provided unless oneTime is true");
        }
    }

    @Override
    public boolean deleteMaintenanceTask(Long vehicleId, Long taskId) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId))
                .map(task -> {
                    maintenanceTaskRepository.deleteById(task.getId());
                    return true;
                })
                .orElse(false);
    }
}

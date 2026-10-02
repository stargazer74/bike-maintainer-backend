package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceTaskStatusHelper;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceTaskStatusResult;
import de.chriswohlbrecht.maintenance.exception.InvalidMaintenanceIntervalException;
import de.chriswohlbrecht.maintenance.mapper.MaintenanceTaskMapper;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTask;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MaintenanceTaskComponentImpl implements IMaintenanceTaskComponent {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final MaintenanceLogTaskRepository maintenanceLogTaskRepository;
    private final MaintenanceTaskMapper maintenanceTaskMapper;
    private final MaintenanceTaskStatusHelper maintenanceTaskStatusHelper;

    @Override
    @Transactional(readOnly = true)
    public Optional<List<MaintenanceTaskResponse>> listMaintenanceTasks(Long vehicleId) {
        return vehicleRepository.findById(vehicleId).map(vehicle -> {
            Map<Long, List<MaintenanceLog>> logsByTaskId = logsByTaskId(vehicleId);
            return maintenanceTaskRepository.findAllByVehicle_Id(vehicleId).stream()
                    .map(task -> toResponseWithStatus(task, vehicle, logsByTaskId))
                    .toList();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceTaskResponse> getMaintenanceTask(Long vehicleId, Long taskId) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId)
                        .map(task -> toResponseWithStatus(task, vehicle, logsByTaskId(vehicleId))));
    }

    private Map<Long, List<MaintenanceLog>> logsByTaskId(Long vehicleId) {
        return maintenanceLogTaskRepository.findAllByLog_Vehicle_Id(vehicleId).stream()
                .collect(Collectors.groupingBy(
                        link -> link.getTask().getId(),
                        Collectors.mapping(MaintenanceLogTask::getLog, Collectors.toList())));
    }

    private MaintenanceTaskResponse toResponseWithStatus(
            MaintenanceTask task, Vehicle vehicle, Map<Long, List<MaintenanceLog>> logsByTaskId) {
        List<MaintenanceLog> logsForTask = logsByTaskId.getOrDefault(task.getId(), List.of());
        MaintenanceTaskStatusResult status = maintenanceTaskStatusHelper.compute(
                task, vehicle.getCurrentMileage(), vehicle.getFirstRegistrationDate(), logsForTask);
        return maintenanceTaskMapper.toResponse(task)
                .status(status.status())
                .progress(status.progress())
                .remainingFraction(status.remainingFraction())
                .kmRemaining(status.kmRemaining())
                .daysRemaining(status.daysRemaining())
                .lastServiceMileage(status.lastServiceMileage())
                .lastServiceDate(status.lastServiceDate());
    }

    @Override
    @Transactional
    public Optional<MaintenanceTaskResponse> createMaintenanceTask(Long vehicleId, MaintenanceTaskRequest request) {
        return vehicleRepository.findById(vehicleId).map(vehicle -> {
            validateInterval(request);
            MaintenanceTask task = maintenanceTaskMapper.toEntity(request);
            task.setVehicle(vehicle);
            MaintenanceTask saved = maintenanceTaskRepository.save(task);
            return toResponseWithStatus(saved, vehicle, logsByTaskId(vehicleId));
        });
    }

    @Override
    @Transactional
    public Optional<MaintenanceTaskResponse> updateMaintenanceTask(Long vehicleId, Long taskId, MaintenanceTaskRequest request) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId)
                        .map(task -> {
                            validateInterval(request);
                            maintenanceTaskMapper.updateEntityFromRequest(request, task);
                            MaintenanceTask saved = maintenanceTaskRepository.save(task);
                            return toResponseWithStatus(saved, vehicle, logsByTaskId(vehicleId));
                        }));
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

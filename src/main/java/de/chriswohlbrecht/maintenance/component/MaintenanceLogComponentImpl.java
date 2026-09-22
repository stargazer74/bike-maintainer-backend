package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.exception.InvalidTaskReferenceException;
import de.chriswohlbrecht.maintenance.mapper.MaintenanceLogMapper;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTask;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTaskId;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MaintenanceLogComponentImpl implements MaintenanceLogComponent {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final MaintenanceLogTaskRepository maintenanceLogTaskRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final MaintenanceLogMapper maintenanceLogMapper;

    @Override
    public Optional<List<MaintenanceLogResponse>> listMaintenanceLogs(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> maintenanceLogRepository.findAllByVehicle_Id(vehicleId).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @Override
    public Optional<MaintenanceLogResponse> getMaintenanceLog(Long vehicleId, Long logId) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> maintenanceLogRepository.findByIdAndVehicle_Id(logId, vehicleId))
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Optional<MaintenanceLogResponse> createMaintenanceLog(Long vehicleId, MaintenanceLogRequest request) {
        return vehicleRepository.findById(vehicleId).map(vehicle -> {
            List<MaintenanceTask> performedTasks = new ArrayList<>();
            for (Long taskId : performedTaskIds(request)) {
                Optional<MaintenanceTask> task = maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId);
                if (task.isEmpty()) {
                    throw new InvalidTaskReferenceException("Task with id " + taskId + " not found for vehicle " + vehicleId);
                }
                performedTasks.add(task.get());
            }

            MaintenanceLog log = maintenanceLogMapper.toEntity(request);
            log.setVehicle(vehicle);
            MaintenanceLog savedLog = maintenanceLogRepository.save(log);
            linkTasks(savedLog, performedTasks);
            return toResponse(savedLog);
        });
    }

    @Override
    @Transactional
    public Optional<MaintenanceLogResponse> updateMaintenanceLog(Long vehicleId, Long logId, MaintenanceLogRequest request) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> maintenanceLogRepository.findByIdAndVehicle_Id(logId, vehicleId))
                .map(log -> {
                    List<MaintenanceTask> performedTasks = new ArrayList<>();
                    for (Long taskId : performedTaskIds(request)) {
                        Optional<MaintenanceTask> task = maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId);
                        if (task.isEmpty()) {
                            throw new InvalidTaskReferenceException("Task with id " + taskId + " not found for vehicle " + vehicleId);
                        }
                        performedTasks.add(task.get());
                    }

                    maintenanceLogMapper.updateEntityFromRequest(request, log);
                    MaintenanceLog savedLog = maintenanceLogRepository.save(log);
                    maintenanceLogTaskRepository.deleteAllByLog_Id(savedLog.getId());
                    linkTasks(savedLog, performedTasks);
                    return toResponse(savedLog);
                });
    }

    @Override
    @Transactional
    public boolean deleteMaintenanceLog(Long vehicleId, Long logId) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> maintenanceLogRepository.findByIdAndVehicle_Id(logId, vehicleId))
                .map(log -> {
                    maintenanceLogTaskRepository.deleteAllByLog_Id(log.getId());
                    maintenanceLogRepository.deleteById(log.getId());
                    return true;
                })
                .orElse(false);
    }

    private static List<Long> performedTaskIds(MaintenanceLogRequest request) {
        return request.getPerformedTaskIds() == null ? List.of() : request.getPerformedTaskIds();
    }

    private void linkTasks(MaintenanceLog log, List<MaintenanceTask> tasks) {
        for (MaintenanceTask task : tasks) {
            MaintenanceLogTaskId linkId = new MaintenanceLogTaskId(log.getId(), task.getId());
            MaintenanceLogTask link = MaintenanceLogTask.builder()
                    .id(linkId)
                    .log(log)
                    .task(task)
                    .build();
            maintenanceLogTaskRepository.save(link);
        }
    }

    private MaintenanceLogResponse toResponse(MaintenanceLog log) {
        List<Long> performedTaskIds = maintenanceLogTaskRepository.findAllByLog_Id(log.getId()).stream()
                .map(link -> link.getTask().getId())
                .toList();
        return maintenanceLogMapper.toResponse(log).performedTaskIds(performedTaskIds);
    }
}

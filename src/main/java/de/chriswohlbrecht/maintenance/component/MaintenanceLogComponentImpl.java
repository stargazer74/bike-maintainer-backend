package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceReportEntry;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceReportPdfHelper;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceReport;
import de.chriswohlbrecht.maintenance.exception.InvalidLogReferenceException;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MaintenanceLogComponentImpl implements IMaintenanceLogComponent {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final MaintenanceLogTaskRepository maintenanceLogTaskRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final MaintenanceLogMapper maintenanceLogMapper;
    private final MaintenanceReportPdfHelper maintenanceReportPdfHelper;
    private final IUserLocaleComponent userLocaleComponent;
    private final ICurrentUserComponent currentUserComponent;

    private static final Comparator<MaintenanceLog> CHRONOLOGICAL = Comparator
            .comparing(MaintenanceLog::getPerformedAt)
            .thenComparing(MaintenanceLog::getId);

    @Override
    public Optional<List<MaintenanceLogResponse>> listMaintenanceLogs(Long vehicleId) {
        return findOwnVehicle(vehicleId)
                .map(vehicle -> maintenanceLogRepository.findAllByVehicle_Id(vehicleId).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @Override
    public Optional<MaintenanceLogResponse> getMaintenanceLog(Long vehicleId, Long logId) {
        return findOwnVehicle(vehicleId)
                .flatMap(vehicle -> maintenanceLogRepository.findByIdAndVehicle_Id(logId, vehicleId))
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Optional<MaintenanceLogResponse> createMaintenanceLog(Long vehicleId, MaintenanceLogRequest request) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
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
        return findOwnVehicle(vehicleId)
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
        return findOwnVehicle(vehicleId)
                .flatMap(vehicle -> maintenanceLogRepository.findByIdAndVehicle_Id(logId, vehicleId))
                .map(log -> {
                    maintenanceLogTaskRepository.deleteAllByLog_Id(log.getId());
                    maintenanceLogRepository.deleteById(log.getId());
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceReport> generateMaintenanceReport(Long vehicleId, List<Long> logIds) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            List<MaintenanceLog> logs = selectLogs(vehicleId, logIds).stream()
                    .sorted(CHRONOLOGICAL)
                    .toList();
            List<MaintenanceReportEntry> entries = logs.stream()
                    .map(log -> new MaintenanceReportEntry(
                            log.getPerformedAt(),
                            log.getMileageAtPerformed(),
                            log.getNotes(),
                            maintenanceLogTaskRepository.findAllByLog_Id(log.getId()).stream()
                                    .map(link -> link.getTask().getName())
                                    .toList()))
                    .toList();
            byte[] content = maintenanceReportPdfHelper.generate(vehicle, entries,
                    userLocaleComponent.localeOf(vehicle.getUser()));
            return new MaintenanceReport(reportFileName(vehicleId, logIds, logs), content);
        });
    }

    /** All logs of the vehicle, or only the selected ones; duplicates are ignored. */
    private List<MaintenanceLog> selectLogs(Long vehicleId, List<Long> logIds) {
        List<MaintenanceLog> all = maintenanceLogRepository.findAllByVehicle_Id(vehicleId);
        if (logIds == null) {
            return all;
        }
        Set<Long> selected = new LinkedHashSet<>(logIds);
        if (selected.isEmpty()) {
            throw new InvalidLogReferenceException("No maintenance log selected for the report");
        }
        Map<Long, MaintenanceLog> logsById = all.stream()
                .collect(Collectors.toMap(MaintenanceLog::getId, log -> log));
        List<MaintenanceLog> result = new ArrayList<>();
        for (Long logId : selected) {
            MaintenanceLog log = logsById.get(logId);
            if (log == null) {
                throw new InvalidLogReferenceException("Log with id " + logId + " not found for vehicle " + vehicleId);
            }
            result.add(log);
        }
        return result;
    }

    /** Like before for several entries; a report of a single entry carries the date of that maintenance. */
    private static String reportFileName(Long vehicleId, List<Long> logIds, List<MaintenanceLog> logs) {
        String base = "maintenance-report-vehicle-" + vehicleId;
        if (logIds != null && logs.size() == 1) {
            return base + "-" + logs.getFirst().getPerformedAt() + ".pdf";
        }
        return base + ".pdf";
    }

    private static Set<Long> performedTaskIds(MaintenanceLogRequest request) {
        return request.getPerformedTaskIds() == null ? Set.of() : request.getPerformedTaskIds();
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

    /** Vehicles of other users are treated like non-existent ones (404), so foreign ids cannot be probed. */
    private Optional<Vehicle> findOwnVehicle(Long vehicleId) {
        return vehicleRepository.findByIdAndUser_Id(vehicleId, currentUserComponent.getCurrentUserId());
    }
}

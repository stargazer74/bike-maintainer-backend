package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskStatus;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceChecklistEntry;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceChecklistPdfHelper;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceTaskStatusHelper;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceTaskStatusResult;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceChecklist;
import de.chriswohlbrecht.maintenance.exception.ConflictException;
import de.chriswohlbrecht.maintenance.exception.InvalidMaintenanceIntervalException;
import de.chriswohlbrecht.maintenance.exception.InvalidTaskReferenceException;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MaintenanceTaskComponentImpl implements IMaintenanceTaskComponent {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final MaintenanceLogTaskRepository maintenanceLogTaskRepository;
    private final MaintenanceTaskMapper maintenanceTaskMapper;
    private final MaintenanceTaskStatusHelper maintenanceTaskStatusHelper;
    private final ICurrentUserComponent currentUserComponent;
    private final MaintenanceChecklistPdfHelper maintenanceChecklistPdfHelper;
    private final IUserLocaleComponent userLocaleComponent;
    private final Clock clock;

    /** Most urgent first: overdue, due soon, the rest; within a status the smallest remaining fraction first. */
    private static final Comparator<MaintenanceChecklistEntry> BY_URGENCY = Comparator
            .comparingInt((MaintenanceChecklistEntry entry) -> urgencyRank(entry.status().status()))
            .thenComparing(entry -> entry.status().remainingFraction(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MaintenanceChecklistEntry::name, String.CASE_INSENSITIVE_ORDER);

    @Override
    @Transactional(readOnly = true)
    public Optional<List<MaintenanceTaskResponse>> listMaintenanceTasks(Long vehicleId) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            Map<Long, List<MaintenanceLog>> logsByTaskId = logsByTaskId(vehicleId);
            return maintenanceTaskRepository.findAllByVehicle_Id(vehicleId).stream()
                    .map(task -> toResponseWithStatus(task, vehicle, logsByTaskId))
                    .toList();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceTaskResponse> getMaintenanceTask(Long vehicleId, Long taskId) {
        return findOwnVehicle(vehicleId)
                .flatMap(vehicle -> maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId)
                        .map(task -> toResponseWithStatus(task, vehicle, logsByTaskId(vehicleId))));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MaintenanceChecklist> generateMaintenanceChecklist(Long vehicleId, List<Long> taskIds) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            Set<Long> selected = new LinkedHashSet<>(taskIds == null ? List.of() : taskIds);
            if (selected.isEmpty()) {
                throw new InvalidTaskReferenceException("No task selected for the checklist");
            }
            Map<Long, MaintenanceTask> tasksById = maintenanceTaskRepository.findAllByVehicle_Id(vehicleId).stream()
                    .collect(Collectors.toMap(MaintenanceTask::getId, task -> task));
            Map<Long, List<MaintenanceLog>> logsByTaskId = logsByTaskId(vehicleId);

            List<MaintenanceChecklistEntry> entries = selected.stream()
                    .map(taskId -> checklistEntry(tasksById.get(taskId), taskId, vehicle, logsByTaskId))
                    .sorted(BY_URGENCY)
                    .toList();

            LocalDate today = LocalDate.now(clock);
            Locale locale = userLocaleComponent.localeOf(vehicle.getUser());
            return new MaintenanceChecklist(
                    maintenanceChecklistPdfHelper.fileName(vehicle, today, locale),
                    maintenanceChecklistPdfHelper.generate(vehicle, entries, today, locale));
        });
    }

    private MaintenanceChecklistEntry checklistEntry(MaintenanceTask task, Long taskId, Vehicle vehicle,
                                                     Map<Long, List<MaintenanceLog>> logsByTaskId) {
        if (task == null) {
            throw new InvalidTaskReferenceException("Task with id " + taskId + " not found for vehicle "
                    + vehicle.getId());
        }
        if (!task.isActive()) {
            throw new InvalidTaskReferenceException("Task with id " + taskId + " is inactive");
        }
        MaintenanceTaskStatusResult status = maintenanceTaskStatusHelper.compute(task, vehicle.getCurrentMileage(),
                vehicle.getFirstRegistrationDate(), logsByTaskId.getOrDefault(task.getId(), List.of()));
        if (status.status() == MaintenanceTaskStatus.DONE) {
            throw new InvalidTaskReferenceException("Task with id " + taskId + " is already done");
        }
        return new MaintenanceChecklistEntry(task.getName(), task.getDescription(), task.isOneTime(),
                task.getIntervalKm(), task.getIntervalMonths(), status);
    }

    private static int urgencyRank(MaintenanceTaskStatus status) {
        return switch (status) {
            case OVERDUE -> 0;
            case DUE_SOON -> 1;
            case OK -> 2;
            case DONE -> 3;
        };
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
        return findOwnVehicle(vehicleId).map(vehicle -> {
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
        return findOwnVehicle(vehicleId)
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
        return findOwnVehicle(vehicleId)
                .flatMap(vehicle -> maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId))
                .map(task -> {
                    // Deleting a performed task would silently remove it from the history; deactivate it instead.
                    if (maintenanceLogTaskRepository.existsByTask_Id(task.getId())) {
                        throw new ConflictException("Maintenance task was already performed");
                    }
                    maintenanceTaskRepository.deleteById(task.getId());
                    return true;
                })
                .orElse(false);
    }

    /** Vehicles of other users are treated like non-existent ones (404), so foreign ids cannot be probed. */
    private Optional<Vehicle> findOwnVehicle(Long vehicleId) {
        return vehicleRepository.findByIdAndUser_Id(vehicleId, currentUserComponent.getCurrentUserId());
    }
}

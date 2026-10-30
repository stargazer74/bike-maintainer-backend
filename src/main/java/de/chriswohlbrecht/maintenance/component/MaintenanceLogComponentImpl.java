package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.CostSummaryEntry;
import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMaterialRequest;
import de.chriswohlbrecht.maintenance.api.model.NewMaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceCostHelper;
import de.chriswohlbrecht.maintenance.component.helper.ReportMaterial;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceReportEntry;
import de.chriswohlbrecht.maintenance.component.helper.MaintenanceReportPdfHelper;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceReport;
import de.chriswohlbrecht.maintenance.exception.InvalidLogReferenceException;
import de.chriswohlbrecht.maintenance.exception.InvalidMaterialException;
import de.chriswohlbrecht.maintenance.exception.InvalidTaskReferenceException;
import de.chriswohlbrecht.maintenance.mapper.MaintenanceLogMapper;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogMaterial;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTask;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLogTaskId;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceTask;
import de.chriswohlbrecht.maintenance.persistence.model.Vehicle;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogMaterialRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceLogTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.MaintenanceTaskRepository;
import de.chriswohlbrecht.maintenance.persistence.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MaintenanceLogComponentImpl implements IMaintenanceLogComponent {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;
    private final MaintenanceLogMaterialRepository maintenanceLogMaterialRepository;
    private final MaintenanceLogTaskRepository maintenanceLogTaskRepository;
    private final MaintenanceTaskRepository maintenanceTaskRepository;
    private final MaintenanceLogMapper maintenanceLogMapper;
    private final MaintenanceReportPdfHelper maintenanceReportPdfHelper;
    private final MaintenanceCostHelper maintenanceCostHelper;
    private final IUserLocaleComponent userLocaleComponent;
    private final ICurrentUserComponent currentUserComponent;
    private final Clock clock;

    private static final int MAX_AMOUNT_SCALE = 2;
    private static final int MAX_QUANTITY_SCALE = 3;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(MAX_AMOUNT_SCALE);

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
            List<MaintenanceTask> performedTasks = existingTasks(vehicleId, request);
            List<MaintenanceLogMaterial> materials = materialsOf(request, performedTasks);

            MaintenanceLog log = maintenanceLogMapper.toEntity(request);
            log.setVehicle(vehicle);
            log.setCurrency(currentUserComponent.getCurrentUser().getCurrency());
            MaintenanceLog savedLog = maintenanceLogRepository.save(log);
            saveMaterials(savedLog, materials);
            performedTasks.addAll(createNewTasks(vehicle, request));
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
                    List<MaintenanceTask> performedTasks = existingTasks(vehicleId, request);
                    List<MaintenanceLogMaterial> materials = materialsOf(request, performedTasks);

                    maintenanceLogMapper.updateEntityFromRequest(request, log);
                    MaintenanceLog savedLog = maintenanceLogRepository.save(log);
                    maintenanceLogMaterialRepository.deleteAllByLog_Id(savedLog.getId());
                    saveMaterials(savedLog, materials);
                    maintenanceLogTaskRepository.deleteAllByLog_Id(savedLog.getId());
                    performedTasks.addAll(createNewTasks(log.getVehicle(), request));
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
                    maintenanceLogMaterialRepository.deleteAllByLog_Id(log.getId());
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
            Map<Long, List<MaintenanceLogMaterial>> materialsByLog = maintenanceLogMaterialRepository
                    .findAllByLog_Vehicle_Id(vehicleId).stream()
                    .sorted(Comparator.comparingInt(MaintenanceLogMaterial::getPosition))
                    .collect(Collectors.groupingBy(material -> material.getLog().getId()));
            List<MaintenanceReportEntry> entries = logs.stream()
                    .map(log -> toReportEntry(log, materialsByLog.getOrDefault(log.getId(), List.of())))
                    .toList();
            byte[] content = maintenanceReportPdfHelper.generate(vehicle, entries, LocalDate.now(clock),
                    userLocaleComponent.localeOf(vehicle.getUser()));
            return new MaintenanceReport(reportFileName(vehicleId, logIds, logs), content);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<List<CostSummaryEntry>> getCostSummary(Long vehicleId) {
        return findOwnVehicle(vehicleId).map(vehicle -> {
            Map<Long, List<MaintenanceLogMaterial>> materialsByLog = maintenanceLogMaterialRepository
                    .findAllByLog_Vehicle_Id(vehicleId).stream()
                    .collect(Collectors.groupingBy(material -> material.getLog().getId()));
            int currentYear = LocalDate.now(clock).getYear();
            Map<String, CostSummaryEntry> entriesByCurrency = new TreeMap<>();
            for (MaintenanceLog log : maintenanceLogRepository.findAllByVehicle_Id(vehicleId)) {
                BigDecimal cost = maintenanceCostHelper.totalCost(
                        materialsByLog.getOrDefault(log.getId(), List.of()), log.getLaborCost());
                if (cost == null) {
                    continue;
                }
                CostSummaryEntry entry = entriesByCurrency.computeIfAbsent(log.getCurrency(),
                        currency -> new CostSummaryEntry(Currency.fromValue(currency), ZERO_AMOUNT, ZERO_AMOUNT));
                entry.setTotal(entry.getTotal().add(cost));
                if (log.getPerformedAt().getYear() == currentYear) {
                    entry.setCurrentYear(entry.getCurrentYear().add(cost));
                }
            }
            entriesByCurrency.values().forEach(entry -> {
                entry.setTotal(entry.getTotal().setScale(MAX_AMOUNT_SCALE, RoundingMode.UNNECESSARY));
                entry.setCurrentYear(entry.getCurrentYear().setScale(MAX_AMOUNT_SCALE, RoundingMode.UNNECESSARY));
            });
            return List.copyOf(entriesByCurrency.values());
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

    /** The performed tasks the request references; all must belong to the vehicle. Checked before anything is saved. */
    private List<MaintenanceTask> existingTasks(Long vehicleId, MaintenanceLogRequest request) {
        List<MaintenanceTask> tasks = new ArrayList<>();
        for (Long taskId : performedTaskIds(request)) {
            Optional<MaintenanceTask> task = maintenanceTaskRepository.findByIdAndVehicle_Id(taskId, vehicleId);
            if (task.isEmpty()) {
                throw new InvalidTaskReferenceException("Task with id " + taskId + " not found for vehicle " + vehicleId);
            }
            tasks.add(task.get());
        }
        return tasks;
    }

    /**
     * The materials of the request as entities (not yet linked to the log). Checked before anything is saved: at most
     * two decimal places for amounts, three for quantities, and a task reference only to a performed task.
     */
    private List<MaintenanceLogMaterial> materialsOf(MaintenanceLogRequest request, List<MaintenanceTask> performedTasks) {
        requireMaxScale(request.getLaborCost(), MAX_AMOUNT_SCALE, "laborCost");
        if (request.getMaterials() == null) {
            return List.of();
        }
        Map<Long, MaintenanceTask> performedById = performedTasks.stream()
                .collect(Collectors.toMap(MaintenanceTask::getId, task -> task));
        List<MaintenanceLogMaterial> materials = new ArrayList<>();
        for (MaintenanceMaterialRequest materialRequest : request.getMaterials()) {
            requireMaxScale(materialRequest.getPrice(), MAX_AMOUNT_SCALE, "price");
            requireMaxScale(materialRequest.getQuantity(), MAX_QUANTITY_SCALE, "quantity");
            MaintenanceLogMaterial material = maintenanceLogMapper.toMaterialEntity(materialRequest);
            if (materialRequest.getTaskId() != null) {
                MaintenanceTask task = performedById.get(materialRequest.getTaskId());
                if (task == null) {
                    throw new InvalidMaterialException("Material task " + materialRequest.getTaskId()
                            + " is not one of the performed tasks");
                }
                material.setTask(task);
            }
            material.setPosition(materials.size());
            materials.add(material);
        }
        return materials;
    }

    private static void requireMaxScale(BigDecimal value, int maxScale, String field) {
        if (value != null && value.stripTrailingZeros().scale() > maxScale) {
            throw new InvalidMaterialException(field + " must not have more than " + maxScale + " decimal places");
        }
    }

    private void saveMaterials(MaintenanceLog log, List<MaintenanceLogMaterial> materials) {
        for (MaintenanceLogMaterial material : materials) {
            material.setLog(log);
        }
        maintenanceLogMaterialRepository.saveAll(materials);
    }

    /**
     * Work that was no task before becomes a task of the vehicle, performed by this log: one-time without an interval,
     * recurring with one (the log then counts as its first performance). Runs in the log's transaction.
     */
    private List<MaintenanceTask> createNewTasks(Vehicle vehicle, MaintenanceLogRequest request) {
        if (request.getNewTasks() == null) {
            return List.of();
        }
        List<MaintenanceTask> created = new ArrayList<>();
        for (NewMaintenanceTaskRequest newTask : request.getNewTasks()) {
            boolean recurring = newTask.getIntervalKm() != null || newTask.getIntervalMonths() != null;
            created.add(maintenanceTaskRepository.save(MaintenanceTask.builder()
                    .vehicle(vehicle)
                    .name(newTask.getName())
                    .description(newTask.getDescription())
                    .intervalKm(newTask.getIntervalKm())
                    .intervalMonths(newTask.getIntervalMonths())
                    .oneTime(!recurring)
                    .active(true)
                    .build()));
        }
        return created;
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

    private MaintenanceReportEntry toReportEntry(MaintenanceLog log, List<MaintenanceLogMaterial> materials) {
        return new MaintenanceReportEntry(
                log.getPerformedAt(),
                log.getMileageAtPerformed(),
                log.getNotes(),
                maintenanceLogTaskRepository.findAllByLog_Id(log.getId()).stream()
                        .map(link -> link.getTask().getName())
                        .toList(),
                materials.stream()
                        .map(m -> new ReportMaterial(m.getName(), m.getPartNumber(), m.getQuantity(), m.getUnit(),
                                m.getPrice()))
                        .toList(),
                log.getLaborCost(),
                maintenanceCostHelper.totalCost(materials, log.getLaborCost()),
                log.getCurrency());
    }

    private MaintenanceLogResponse toResponse(MaintenanceLog log) {
        List<Long> performedTaskIds = maintenanceLogTaskRepository.findAllByLog_Id(log.getId()).stream()
                .map(link -> link.getTask().getId())
                .toList();
        List<MaintenanceLogMaterial> materials = maintenanceLogMaterialRepository
                .findAllByLog_IdOrderByPosition(log.getId());
        return maintenanceLogMapper.toResponse(log)
                .performedTaskIds(performedTaskIds)
                .materials(materials.stream().map(maintenanceLogMapper::toMaterialResponse).toList())
                .totalCost(maintenanceCostHelper.totalCost(materials, log.getLaborCost()));
    }

    /** Vehicles of other users are treated like non-existent ones (404), so foreign ids cannot be probed. */
    private Optional<Vehicle> findOwnVehicle(Long vehicleId) {
        return vehicleRepository.findByIdAndUser_Id(vehicleId, currentUserComponent.getCurrentUserId());
    }
}

package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceLog;
import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceMaterial;
import de.chriswohlbrecht.maintenance.api.model.ExportedMaintenanceTask;
import de.chriswohlbrecht.maintenance.api.model.ExportedVehicle;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMaterialRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleExport;
import de.chriswohlbrecht.maintenance.api.model.VehicleResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleSpecifications;
import de.chriswohlbrecht.maintenance.component.helper.VehicleFileNames;
import de.chriswohlbrecht.maintenance.component.model.VehicleExportFile;
import de.chriswohlbrecht.maintenance.exception.ImportFormatUnsupportedException;
import de.chriswohlbrecht.maintenance.exception.ImportInvalidException;
import de.chriswohlbrecht.maintenance.exception.InvalidMaintenanceIntervalException;
import de.chriswohlbrecht.maintenance.exception.InvalidMaterialException;
import de.chriswohlbrecht.maintenance.exception.InvalidMeasurementException;
import de.chriswohlbrecht.maintenance.exception.InvalidTaskReferenceException;
import de.chriswohlbrecht.maintenance.mapper.VehicleExportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Export and import of a vehicle (Epic #82). The import goes through the normal components, so the same rules apply
 * as for single requests; it runs in one transaction, so a problem anywhere creates nothing.
 */
@Component
@RequiredArgsConstructor
public class VehicleExportComponentImpl implements IVehicleExportComponent {

    private static final String TASK_REF_PREFIX = "t";

    private final IVehicleComponent vehicleComponent;
    private final IMaintenanceTaskComponent maintenanceTaskComponent;
    private final IMaintenanceLogComponent maintenanceLogComponent;
    private final IMeasurementComponent measurementComponent;
    private final VehicleExportMapper vehicleExportMapper;
    private final Environment environment;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleExportFile> exportVehicle(Long vehicleId) {
        return vehicleComponent.getVehicle(vehicleId).map(vehicle -> {
            List<MaintenanceTaskResponse> tasks = maintenanceTaskComponent.listMaintenanceTasks(vehicleId)
                    .orElseThrow().stream()
                    .sorted(Comparator.comparing(MaintenanceTaskResponse::getId))
                    .toList();
            Map<Long, String> refs = new HashMap<>();
            List<ExportedMaintenanceTask> exportedTasks = new ArrayList<>();
            for (MaintenanceTaskResponse task : tasks) {
                String ref = TASK_REF_PREFIX + (exportedTasks.size() + 1);
                refs.put(task.getId(), ref);
                exportedTasks.add(vehicleExportMapper.toExportedTask(task).ref(ref));
            }
            List<ExportedMaintenanceLog> exportedLogs = maintenanceLogComponent.listMaintenanceLogs(vehicleId)
                    .orElseThrow().stream()
                    .sorted(Comparator.comparing(MaintenanceLogResponse::getPerformedAt)
                            .thenComparing(MaintenanceLogResponse::getId))
                    .map(log -> toExportedLog(log, refs))
                    .toList();

            ExportedVehicle exportedVehicle = vehicleExportMapper.toExportedVehicle(vehicle)
                    .specifications(storedSpecifications(vehicleId))
                    .maintenanceTasks(exportedTasks)
                    .maintenanceLogs(exportedLogs);
            VehicleExport file = new VehicleExport()
                    .formatVersion(ImportFormatUnsupportedException.SUPPORTED_VERSION)
                    .exportedAt(OffsetDateTime.now(clock))
                    .appVersion(environment.getProperty("info.app.version"))
                    .vehicle(exportedVehicle);
            String fileName = VehicleFileNames.fileName("vehicle", vehicle.getName(), LocalDate.now(clock), "json");
            return new VehicleExportFile(fileName, file);
        });
    }

    @Override
    @Transactional
    public VehicleResponse importVehicle(VehicleExport file) {
        if (!Integer.valueOf(ImportFormatUnsupportedException.SUPPORTED_VERSION).equals(file.getFormatVersion())) {
            throw new ImportFormatUnsupportedException(file.getFormatVersion());
        }
        ExportedVehicle vehicle = file.getVehicle();
        List<ExportedMaintenanceTask> tasks = orEmpty(vehicle.getMaintenanceTasks());
        List<ExportedMaintenanceLog> logs = orEmpty(vehicle.getMaintenanceLogs());
        checkReferences(tasks, logs);

        VehicleResponse created = vehicleComponent.createVehicle(vehicleExportMapper.toVehicleRequest(vehicle));
        Long vehicleId = created.getId();
        if (vehicle.getSpecifications() != null) {
            at("vehicle.specifications",
                    () -> measurementComponent.updateVehicleSpecifications(vehicleId, vehicle.getSpecifications()));
        }
        Map<String, Long> taskIds = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            ExportedMaintenanceTask task = tasks.get(i);
            Long taskId = at("vehicle.maintenanceTasks[" + i + "]", () -> maintenanceTaskComponent
                    .createMaintenanceTask(vehicleId, vehicleExportMapper.toTaskRequest(task))
                    .orElseThrow()
                    .getId());
            taskIds.put(task.getRef(), taskId);
        }
        for (int i = 0; i < logs.size(); i++) {
            ExportedMaintenanceLog log = logs.get(i);
            MaintenanceLogRequest request = toLogRequest(log, taskIds);
            at("vehicle.maintenanceLogs[" + i + "]",
                    () -> maintenanceLogComponent.createMaintenanceLog(vehicleId, request, log.getCurrency()));
        }
        return created;
    }

    /** {@code null} if no target value is stored, so the file does not carry an empty object. */
    private VehicleSpecifications storedSpecifications(Long vehicleId) {
        return measurementComponent.getVehicleSpecifications(vehicleId)
                .filter(specifications -> !specifications.equals(new VehicleSpecifications()))
                .orElse(null);
    }

    private ExportedMaintenanceLog toExportedLog(MaintenanceLogResponse log, Map<Long, String> refs) {
        Set<String> performedTaskRefs = new LinkedHashSet<>();
        orEmpty(log.getPerformedTaskIds()).stream().sorted().map(refs::get).forEach(performedTaskRefs::add);
        List<ExportedMaintenanceMaterial> materials = orEmpty(log.getMaterials()).stream()
                .map(material -> vehicleExportMapper.toExportedMaterial(material)
                        .taskRef(material.getTaskId() == null ? null : refs.get(material.getTaskId())))
                .toList();
        return vehicleExportMapper.toExportedLog(log)
                .performedTaskRefs(performedTaskRefs)
                .materials(materials);
    }

    private MaintenanceLogRequest toLogRequest(ExportedMaintenanceLog log, Map<String, Long> taskIds) {
        Set<Long> performedTaskIds = new LinkedHashSet<>();
        orEmpty(log.getPerformedTaskRefs()).forEach(ref -> performedTaskIds.add(taskIds.get(ref)));
        List<MaintenanceMaterialRequest> materials = orEmpty(log.getMaterials()).stream()
                .map(material -> vehicleExportMapper.toMaterialRequest(material)
                        .taskId(material.getTaskRef() == null ? null : taskIds.get(material.getTaskRef())))
                .toList();
        return vehicleExportMapper.toLogRequest(log)
                .performedTaskIds(performedTaskIds)
                .materials(materials);
    }

    /**
     * The references inside the file, all problems at once: every {@code ref} only once, logs refer to known tasks,
     * a material only to a task its log performed.
     */
    private static void checkReferences(List<ExportedMaintenanceTask> tasks, List<ExportedMaintenanceLog> logs) {
        List<String> problems = new ArrayList<>();
        Set<String> refs = new HashSet<>();
        for (int i = 0; i < tasks.size(); i++) {
            if (!refs.add(tasks.get(i).getRef())) {
                problems.add("vehicle.maintenanceTasks[" + i + "].ref: duplicate ref \"" + tasks.get(i).getRef() + "\"");
            }
        }
        for (int i = 0; i < logs.size(); i++) {
            ExportedMaintenanceLog log = logs.get(i);
            Set<String> performed = orEmpty(log.getPerformedTaskRefs());
            for (String ref : performed) {
                if (!refs.contains(ref)) {
                    problems.add("vehicle.maintenanceLogs[" + i + "].performedTaskRefs: unknown task ref \"" + ref + "\"");
                }
            }
            List<ExportedMaintenanceMaterial> materials = orEmpty(log.getMaterials());
            for (int m = 0; m < materials.size(); m++) {
                String taskRef = materials.get(m).getTaskRef();
                if (taskRef != null && !performed.contains(taskRef)) {
                    problems.add("vehicle.maintenanceLogs[" + i + "].materials[" + m
                            + "].taskRef: task \"" + taskRef + "\" is not performed by this log");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new ImportInvalidException(problems);
        }
    }

    /** Runs a step of the import; a rule it breaks is reported with the location in the file. */
    private static <T> T at(String location, Supplier<T> step) {
        try {
            return step.get();
        } catch (InvalidMaintenanceIntervalException | InvalidMaterialException | InvalidMeasurementException
                 | InvalidTaskReferenceException e) {
            throw new ImportInvalidException(List.of(location + ": " + e.getMessage()));
        }
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static <T> Set<T> orEmpty(Set<T> set) {
        return set == null ? Set.of() : set;
    }
}

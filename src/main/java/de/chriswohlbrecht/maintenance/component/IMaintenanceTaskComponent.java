package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceTaskResponse;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceChecklist;

import java.util.List;
import java.util.Optional;

public interface IMaintenanceTaskComponent {

    Optional<List<MaintenanceTaskResponse>> listMaintenanceTasks(Long vehicleId);

    Optional<MaintenanceTaskResponse> getMaintenanceTask(Long vehicleId, Long taskId);

    Optional<MaintenanceTaskResponse> createMaintenanceTask(Long vehicleId, MaintenanceTaskRequest request);

    Optional<MaintenanceTaskResponse> updateMaintenanceTask(Long vehicleId, Long taskId, MaintenanceTaskRequest request);

    boolean deleteMaintenanceTask(Long vehicleId, Long taskId);

    /**
     * Printable checklist of the selected tasks, most urgent first.
     *
     * @return empty if the vehicle does not exist or belongs to another user
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidTaskReferenceException no task selected, or a task
     *         that does not belong to the vehicle, is inactive or is a one-time task that is already done
     */
    Optional<MaintenanceChecklist> generateMaintenanceChecklist(Long vehicleId, List<Long> taskIds);
}

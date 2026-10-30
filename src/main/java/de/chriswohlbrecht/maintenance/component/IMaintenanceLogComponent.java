package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceReport;

import java.util.List;
import java.util.Optional;

public interface IMaintenanceLogComponent {

    Optional<List<MaintenanceLogResponse>> listMaintenanceLogs(Long vehicleId);

    Optional<MaintenanceLogResponse> getMaintenanceLog(Long vehicleId, Long logId);

    Optional<MaintenanceLogResponse> createMaintenanceLog(Long vehicleId, MaintenanceLogRequest request);

    Optional<MaintenanceLogResponse> updateMaintenanceLog(Long vehicleId, Long logId, MaintenanceLogRequest request);

    boolean deleteMaintenanceLog(Long vehicleId, Long logId);

    /**
     * Report of the performed maintenance in chronological order.
     *
     * @param logIds entries to put in the report; {@code null} for all entries of the vehicle
     * @return empty if the vehicle does not exist or belongs to another user
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidLogReferenceException empty selection, or an entry
     *         that does not belong to the vehicle
     */
    Optional<MaintenanceReport> generateMaintenanceReport(Long vehicleId, List<Long> logIds);
}

package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.CostSummaryEntry;
import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogRequest;
import de.chriswohlbrecht.maintenance.component.model.LogCreation;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceLogResponse;
import de.chriswohlbrecht.maintenance.component.model.MaintenanceReport;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface IMaintenanceLogComponent {

    Optional<List<MaintenanceLogResponse>> listMaintenanceLogs(Long vehicleId);

    Optional<MaintenanceLogResponse> getMaintenanceLog(Long vehicleId, Long logId);

    /**
     * Creates the log, or returns the existing one if the vehicle already has a log with the request's
     * {@code clientEntryId}. Empty for unknown and foreign vehicles.
     */
    Optional<LogCreation> createMaintenanceLog(Long vehicleId, MaintenanceLogRequest request);

    /**
     * Like {@link #createMaintenanceLog(Long, MaintenanceLogRequest)}, but the costs are in {@code currency} instead of
     * the current user's currency, and the performed one-time tasks {@code createdTaskIds} count as further work of
     * this log (import of a log written elsewhere).
     */
    Optional<MaintenanceLogResponse> createMaintenanceLog(Long vehicleId, MaintenanceLogRequest request, Currency currency,
                                                         Set<Long> createdTaskIds);

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

    /**
     * Costs of the vehicle per currency (sorted by currency code): total and of the current year. Entries without
     * any amount are ignored.
     *
     * @return empty if the vehicle does not exist or belongs to another user
     */
    Optional<List<CostSummaryEntry>> getCostSummary(Long vehicleId);
}

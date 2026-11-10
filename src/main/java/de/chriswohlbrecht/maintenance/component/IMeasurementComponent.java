package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.LatestMeasurementsResponse;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMeasurementsRequest;
import de.chriswohlbrecht.maintenance.api.model.MaintenanceMeasurementsResponse;
import de.chriswohlbrecht.maintenance.api.model.ShimCalculationRequest;
import de.chriswohlbrecht.maintenance.api.model.ShimCalculationResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleSpecifications;
import de.chriswohlbrecht.maintenance.persistence.model.MaintenanceLog;

import java.util.Map;
import java.util.Optional;

/** Target values of a vehicle, measured values of its maintenance logs and the shim calculator (Epic #113). */
public interface IMeasurementComponent {

    /** @return empty if the vehicle does not exist or belongs to another user */
    Optional<VehicleSpecifications> getVehicleSpecifications(Long vehicleId);

    /**
     * Replaces all target values.
     *
     * @return empty if the vehicle does not exist or belongs to another user
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidMeasurementException too many decimals or min > max
     */
    Optional<VehicleSpecifications> updateVehicleSpecifications(Long vehicleId, VehicleSpecifications request);

    /** @return empty if the vehicle does not exist or belongs to another user */
    Optional<LatestMeasurementsResponse> getLatestMeasurements(Long vehicleId);

    /** @return empty if the vehicle does not exist or belongs to another user */
    Optional<ShimCalculationResponse> calculateShims(Long vehicleId, ShimCalculationRequest request);

    /**
     * Replaces the measurements of a log the caller has already checked; {@code null} keeps them unchanged.
     *
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidMeasurementException too many decimals or a valve twice
     */
    void replaceMeasurements(MaintenanceLog log, MaintenanceMeasurementsRequest request);

    /** The measurements of a log with their status, or null if it has none. */
    MaintenanceMeasurementsResponse measurementsOf(MaintenanceLog log);

    /** The measurements of all logs of a vehicle by log ID (logs without measurements are left out). */
    Map<Long, MaintenanceMeasurementsResponse> measurementsByLog(Long vehicleId);
}

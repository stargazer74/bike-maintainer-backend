package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateRequest;
import de.chriswohlbrecht.maintenance.api.model.ApplyTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleTemplateSummaryResponse;
import de.chriswohlbrecht.maintenance.api.model.VehicleType;

import java.util.List;
import java.util.Optional;

/** Search and view of the maintenance schedule templates. */
public interface IVehicleTemplateComponent {

    /**
     * Templates of a make, sorted by make, model and first model year.
     *
     * @param make  compared exactly after normalising spelling
     * @param type  optional
     * @param model optional, contained in the model name or one of its aliases (after normalising)
     * @param year  optional, within the model years of the template
     */
    List<VehicleTemplateSummaryResponse> search(String make, VehicleType type, String model, Integer year);

    /** One template with texts in the language of the current user. */
    Optional<VehicleTemplateResponse> get(String templateId);

    /**
     * Creates the selected tasks of a template for a vehicle; tasks the vehicle already has from the template
     * (active or not) are skipped.
     *
     * @return empty if the vehicle or the template does not exist
     * @throws de.chriswohlbrecht.maintenance.exception.InvalidTemplateRequestException unknown or duplicate key,
     *         {@code lastDoneKm} above the mileage, {@code lastDoneDate} in the future or before the first
     *         registration
     */
    Optional<ApplyTemplateResponse> apply(Long vehicleId, ApplyTemplateRequest request);
}

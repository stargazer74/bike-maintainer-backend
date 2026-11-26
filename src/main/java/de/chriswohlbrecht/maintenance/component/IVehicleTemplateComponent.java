package de.chriswohlbrecht.maintenance.component;

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
}

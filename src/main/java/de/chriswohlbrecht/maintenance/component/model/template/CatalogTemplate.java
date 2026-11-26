package de.chriswohlbrecht.maintenance.component.model.template;

import de.chriswohlbrecht.maintenance.api.model.VehicleType;

import java.util.List;

/**
 * A maintenance schedule of one model and range of model years, as stored in the catalog.
 *
 * @param aliases further spellings of the model for the search
 * @param market  e.g. {@code EU}; intervals are in km
 * @param notes   may be {@code null}
 */
public record CatalogTemplate(String id, VehicleType type, String make, String model, List<String> aliases,
                              CatalogYears years, String market, List<CatalogSource> sources,
                              List<CatalogTask> tasks, LocalizedText notes) {

    public CatalogTemplate {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        sources = sources == null ? List.of() : List.copyOf(sources);
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}

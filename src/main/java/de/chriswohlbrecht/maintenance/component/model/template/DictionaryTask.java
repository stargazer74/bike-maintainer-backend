package de.chriswohlbrecht.maintenance.component.model.template;

import de.chriswohlbrecht.maintenance.api.model.VehicleType;

import java.util.List;
import java.util.Map;

/**
 * Name and description of a task key.
 *
 * @param plausible per vehicle type and {@code intervalKm}/{@code intervalMonths} the smallest and largest
 *                  plausible value, both inclusive; no entry means no check
 */
public record DictionaryTask(LocalizedText name, LocalizedText description,
                             Map<VehicleType, Map<String, List<Integer>>> plausible) {

    public DictionaryTask {
        plausible = plausible == null ? Map.of() : Map.copyOf(plausible);
    }
}

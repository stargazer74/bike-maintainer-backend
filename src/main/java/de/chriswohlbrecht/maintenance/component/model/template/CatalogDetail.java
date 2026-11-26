package de.chriswohlbrecht.maintenance.component.model.template;

import java.util.List;

/**
 * Grade, quantity, part or setting of a task.
 *
 * @param label   key into {@link CatalogDictionary#details()}
 * @param sources ids of {@link CatalogSource}
 */
public record CatalogDetail(String label, String value, String quantity, List<String> sources) {

    public CatalogDetail {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}

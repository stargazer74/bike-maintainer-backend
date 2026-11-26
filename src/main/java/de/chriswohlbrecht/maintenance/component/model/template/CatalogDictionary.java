package de.chriswohlbrecht.maintenance.component.model.template;

import java.util.Map;

/**
 * Dictionary of the catalog ({@code tasks.yaml}).
 *
 * @param tasks   by task key
 * @param details by detail label
 */
public record CatalogDictionary(Map<String, DictionaryTask> tasks, Map<String, LocalizedText> details) {

    public CatalogDictionary {
        tasks = tasks == null ? Map.of() : Map.copyOf(tasks);
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
